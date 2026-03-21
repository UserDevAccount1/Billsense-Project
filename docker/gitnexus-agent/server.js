const express = require('express');
const cors = require('cors');
const puppeteer = require('puppeteer');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3002;
const GITNEXUS_URL = 'https://gitnexus.vercel.app';

let browser = null;

async function getBrowser() {
  if (!browser || !browser.connected) {
    browser = await puppeteer.launch({
      headless: 'new',
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-dev-shm-usage',
        '--disable-gpu'
      ]
    });
  }
  return browser;
}

// Health check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', service: 'gitnexus-agent' });
});

/**
 * POST /api/auto-clone
 * Body: { repo: "https://github.com/owner/repo", pat: "ghp_xxx", geminiKey: "AIza..." }
 *
 * This agent:
 * 1. Opens GitNexus in a headless browser
 * 2. Clicks the "GitHub URL" tab
 * 3. Fills the repo URL input
 * 4. Fills the PAT input (if provided)
 * 5. Clicks "Clone Repository"
 * 6. Waits for cloning to complete
 * 7. Returns status + screenshot
 */
app.post('/api/auto-clone', async (req, res) => {
  const { repo, pat, geminiKey } = req.body;

  if (!repo) {
    return res.status(400).json({ error: 'repo URL is required' });
  }

  console.log(`[Agent] Auto-clone request: ${repo}`);

  let page = null;
  try {
    const b = await getBrowser();
    page = await b.newPage();
    await page.setViewport({ width: 1280, height: 900 });

    // Step 1: Navigate to GitNexus
    console.log('[Agent] Step 1: Navigating to GitNexus...');
    await page.goto(GITNEXUS_URL, { waitUntil: 'networkidle2', timeout: 30000 });
    await page.waitForSelector('input', { timeout: 15000 });

    // Step 2: Click the "GitHub URL" tab
    console.log('[Agent] Step 2: Clicking GitHub URL tab...');
    const tabs = await page.$$('button, [role="tab"]');
    for (const tab of tabs) {
      const text = await page.evaluate(el => el.textContent || '', tab);
      if (text.toLowerCase().includes('github') && text.toLowerCase().includes('url')) {
        await tab.click();
        await new Promise(r => setTimeout(r, 500));
        break;
      }
    }

    // Step 3: Find and fill the repo URL input
    console.log('[Agent] Step 3: Filling repo URL...');
    const filled = await page.evaluate((repoUrl, patValue) => {
      const inputs = document.querySelectorAll('input');
      let repoFilled = false;
      let patFilled = false;

      for (const input of inputs) {
        if (input.type === 'hidden' || input.type === 'checkbox' || input.type === 'radio') continue;
        const ph = (input.placeholder || '').toLowerCase();

        // Repo URL field
        if (!repoFilled && (ph.includes('repo') || ph.includes('url') || ph.includes('owner') || ph.includes('github'))) {
          // Use React-compatible value setting
          const nativeSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
          nativeSetter.call(input, repoUrl);
          const tracker = input._valueTracker;
          if (tracker) tracker.setValue('');
          input.dispatchEvent(new Event('input', { bubbles: true }));
          input.dispatchEvent(new Event('change', { bubbles: true }));
          repoFilled = true;
        }

        // PAT field
        if (!patFilled && patValue && (ph.includes('pat') || ph.includes('token') || ph.includes('private') || ph.includes('access'))) {
          const nativeSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
          nativeSetter.call(input, patValue);
          const tracker = input._valueTracker;
          if (tracker) tracker.setValue('');
          input.dispatchEvent(new Event('input', { bubbles: true }));
          input.dispatchEvent(new Event('change', { bubbles: true }));
          patFilled = true;
        }
      }

      // Fallback: fill by position
      const visibleInputs = Array.from(inputs).filter(i =>
        i.type !== 'hidden' && i.type !== 'checkbox' && i.type !== 'radio' && i.offsetParent !== null
      );
      if (!repoFilled && visibleInputs.length >= 1) {
        const nativeSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        nativeSetter.call(visibleInputs[0], repoUrl);
        const tracker = visibleInputs[0]._valueTracker;
        if (tracker) tracker.setValue('');
        visibleInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
        visibleInputs[0].dispatchEvent(new Event('change', { bubbles: true }));
        repoFilled = true;
      }
      if (!patFilled && patValue && visibleInputs.length >= 2) {
        const nativeSetter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
        nativeSetter.call(visibleInputs[1], patValue);
        const tracker = visibleInputs[1]._valueTracker;
        if (tracker) tracker.setValue('');
        visibleInputs[1].dispatchEvent(new Event('input', { bubbles: true }));
        visibleInputs[1].dispatchEvent(new Event('change', { bubbles: true }));
        patFilled = true;
      }

      return { repoFilled, patFilled };
    }, repo, pat);

    console.log(`[Agent] Fill result: repo=${filled.repoFilled}, pat=${filled.patFilled}`);

    // Step 4: Click "Clone Repository" button
    console.log('[Agent] Step 4: Clicking Clone Repository...');
    await new Promise(r => setTimeout(r, 300));

    const cloneClicked = await page.evaluate(() => {
      const buttons = document.querySelectorAll('button');
      for (const btn of buttons) {
        const text = (btn.textContent || '').toLowerCase();
        if (text.includes('clone') && !btn.disabled) {
          btn.click();
          return true;
        }
      }
      return false;
    });

    console.log(`[Agent] Clone button clicked: ${cloneClicked}`);

    // Step 5: Wait for clone + parsing to complete (poll until done or timeout)
    console.log('[Agent] Step 5: Waiting for clone & parse to finish...');
    let pageStatus = 'cloning';
    const maxWait = 120000; // 2 minutes max
    const startTime = Date.now();

    while (Date.now() - startTime < maxWait) {
      await new Promise(r => setTimeout(r, 3000));

      pageStatus = await page.evaluate(() => {
        const body = document.body.innerText.toLowerCase();
        // Check if graph/visualization is showing (clone complete)
        if (body.includes('knowledge graph') || body.includes('nodes') ||
            document.querySelector('canvas') || document.querySelector('svg[class*="graph"]') ||
            document.querySelector('[class*="graph"]') || document.querySelector('[class*="viz"]')) {
          // Check if still loading/parsing
          const pctMatch = body.match(/(\d+)%/);
          if (pctMatch && parseInt(pctMatch[1]) < 100) return 'parsing';
          return 'complete';
        }
        if (body.includes('parsing') || body.includes('cloning') || body.includes('loading')) return 'parsing';
        if (body.includes('error') || body.includes('failed')) return 'error';
        return 'loading';
      });

      console.log(`[Agent] Status: ${pageStatus} (${Math.round((Date.now() - startTime)/1000)}s)`);

      if (pageStatus === 'complete' || pageStatus === 'error') break;
    }

    // Step 6: Take final screenshot
    const screenshot = await page.screenshot({ encoding: 'base64', type: 'png', fullPage: false });
    console.log(`[Agent] Final status: ${pageStatus}`);

    // Keep the page open so we can take more screenshots later
    // Store page reference for the /api/live-view endpoint
    if (!global.activePage || global.activePage.isClosed()) {
      global.activePage = page;
    } else {
      await page.close();
    }

    res.json({
      success: true,
      filled: filled,
      cloneClicked: cloneClicked,
      status: pageStatus,
      screenshot: `data:image/png;base64,${screenshot}`,
      message: `Repo ${filled.repoFilled ? 'filled' : 'NOT filled'}, PAT ${filled.patFilled ? 'filled' : 'skipped'}, Clone ${cloneClicked ? 'clicked' : 'NOT found'}`
    });

  } catch (error) {
    console.error('[Agent] Error:', error.message);

    // Try to take error screenshot
    let screenshot = null;
    try {
      if (page) screenshot = await page.screenshot({ encoding: 'base64', type: 'png' });
    } catch (e) {}

    if (page) {
      try { await page.close(); } catch (e) {}
    }

    res.status(500).json({
      success: false,
      error: error.message,
      screenshot: screenshot ? `data:image/png;base64,${screenshot}` : null
    });
  }
});

/**
 * GET /api/live-view
 * Returns a fresh screenshot of the active GitNexus session (if any).
 * This lets the admin panel show a "live" view of the knowledge graph
 * instead of an iframe.
 */
app.get('/api/live-view', async (req, res) => {
  try {
    if (!global.activePage || global.activePage.isClosed()) {
      return res.status(404).json({ error: 'No active GitNexus session. Click Auto Clone first.' });
    }
    const screenshot = await global.activePage.screenshot({ encoding: 'base64', type: 'png' });
    const status = await global.activePage.evaluate(() => {
      const body = document.body.innerText.toLowerCase();
      if (document.querySelector('canvas') || body.includes('knowledge graph')) return 'complete';
      if (body.includes('parsing') || body.includes('cloning')) return 'parsing';
      return 'ready';
    });
    res.json({
      screenshot: `data:image/png;base64,${screenshot}`,
      status
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * POST /api/screenshot
 * Takes a screenshot of any URL
 */
app.post('/api/screenshot', async (req, res) => {
  const { url } = req.body;
  try {
    const b = await getBrowser();
    const page = await b.newPage();
    await page.setViewport({ width: 1280, height: 900 });
    await page.goto(url || GITNEXUS_URL, { waitUntil: 'networkidle2', timeout: 30000 });
    const screenshot = await page.screenshot({ encoding: 'base64', type: 'png' });
    await page.close();
    res.json({ screenshot: `data:image/png;base64,${screenshot}` });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Graceful shutdown
process.on('SIGTERM', async () => {
  if (browser) await browser.close();
  process.exit(0);
});

app.listen(PORT, () => {
  console.log(`[GitNexus Agent] Running on port ${PORT}`);
  console.log(`[GitNexus Agent] POST /api/auto-clone - Auto-fill and clone repo`);
  console.log(`[GitNexus Agent] GET /health - Health check`);
});
