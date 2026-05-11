#!/usr/bin/env node
/**
 * BillSense cPanel MCP server.
 *
 * Wraps the cPanel UAPI as MCP tools so Claude can drive it natively.
 *
 * Credentials come from env (set in .mcp.json):
 *   CPANEL_HOST   e.g. billsense.dev-environment.site
 *   CPANEL_PORT   default 2083
 *   CPANEL_USER   the cPanel account username (NOT the FTP user — the panel user)
 *   CPANEL_TOKEN  the API token from cPanel → Manage API Tokens
 *
 * Auth header: Authorization: cpanel <user>:<token>
 */

import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import https from "node:https";

const HOST  = process.env.CPANEL_HOST  || "billsense.dev-environment.site";
const PORT  = parseInt(process.env.CPANEL_PORT || "2083", 10);
const USER  = process.env.CPANEL_USER  || "";
const TOKEN = process.env.CPANEL_TOKEN || "";
// iFastNet's cPanel uses a cert that often fails strict verification; opt-in relax.
const STRICT_SSL = process.env.CPANEL_STRICT_SSL !== "false";

function callUapi(module, fn, params = {}, method = "GET") {
  return new Promise((resolve, reject) => {
    if (!USER)  return reject(new Error("CPANEL_USER not set"));
    if (!TOKEN) return reject(new Error("CPANEL_TOKEN not set"));

    const qs = new URLSearchParams(params).toString();
    const path = `/execute/${encodeURIComponent(module)}/${encodeURIComponent(fn)}${
      method === "GET" && qs ? `?${qs}` : ""
    }`;

    const opts = {
      hostname: HOST,
      port: PORT,
      method,
      path,
      headers: {
        Authorization: `cpanel ${USER}:${TOKEN}`,
        "User-Agent": "billsense-cpanel-mcp/0.1",
        Accept: "application/json",
      },
      rejectUnauthorized: STRICT_SSL,
    };
    if (method !== "GET" && qs) {
      opts.headers["Content-Type"] = "application/x-www-form-urlencoded";
      opts.headers["Content-Length"] = Buffer.byteLength(qs);
    }

    const req = https.request(opts, (res) => {
      const chunks = [];
      res.on("data", (c) => chunks.push(c));
      res.on("end", () => {
        const body = Buffer.concat(chunks).toString("utf8");
        let parsed;
        try { parsed = JSON.parse(body); } catch { parsed = { raw: body.slice(0, 4000) }; }
        if (res.statusCode >= 400) {
          return reject(new Error(
            `HTTP ${res.statusCode} ${res.statusMessage} — ${JSON.stringify(parsed).slice(0, 600)}`
          ));
        }
        resolve(parsed);
      });
    });
    req.on("error", reject);
    if (method !== "GET" && qs) req.write(qs);
    req.end();
  });
}

const tools = [
  {
    name: "cpanel_whoami",
    description:
      "Return the authenticated cPanel user, account info, document root, quota, and home directory. " +
      "Use this first to verify credentials are working.",
    inputSchema: { type: "object", properties: {} },
    handler: () => callUapi("Variables", "get_user_information"),
  },
  {
    name: "cpanel_list_addon_domains",
    description: "List addon and parked domains on this cPanel account.",
    inputSchema: { type: "object", properties: {} },
    handler: () => callUapi("DomainInfo", "list_domains"),
  },
  {
    name: "cpanel_list_ftp_accounts",
    description: "List FTP accounts on this cPanel account.",
    inputSchema: { type: "object", properties: {} },
    handler: () => callUapi("Ftp", "list_ftp"),
  },
  {
    name: "cpanel_create_ftp_account",
    description:
      "Create an FTP account scoped to a specific directory. Use this to create a least-privilege " +
      "deploy account for the GitHub Actions cPanel workflow.",
    inputSchema: {
      type: "object",
      properties: {
        user:     { type: "string", description: "FTP username (will be suffixed with @domain by cPanel)" },
        pass:     { type: "string", description: "Password for the account" },
        homedir:  { type: "string", description: "Home directory relative to account root, e.g. 'public_html/billsense'" },
        quota:    { type: "number", description: "Quota in MB; 0 = unlimited", default: 0 },
      },
      required: ["user", "pass", "homedir"],
    },
    handler: (args) =>
      callUapi("Ftp", "add_ftp", {
        user: args.user,
        pass: args.pass,
        homedir: args.homedir,
        quota: args.quota ?? 0,
      }, "POST"),
  },
  {
    name: "cpanel_list_dir",
    description: "List the contents of a directory under the account root.",
    inputSchema: {
      type: "object",
      properties: {
        dir: { type: "string", description: "Directory path, e.g. 'public_html'" },
      },
      required: ["dir"],
    },
    handler: (args) => callUapi("Fileman", "list_files", { dir: args.dir, types: "file|dir" }),
  },
  {
    name: "cpanel_node_apps",
    description: "List all Phusion Passenger Node.js apps configured for this account.",
    inputSchema: { type: "object", properties: {} },
    handler: () => callUapi("NodeJS", "list_applications"),
  },
  {
    name: "cpanel_disk_usage",
    description: "Report disk usage and inode counts.",
    inputSchema: { type: "object", properties: {} },
    handler: () => callUapi("DiskUsage", "get_disk_usage"),
  },
  {
    name: "cpanel_raw_uapi",
    description:
      "Escape hatch — call any UAPI module/function directly. " +
      "Use this only when no dedicated tool exists. method defaults to GET.",
    inputSchema: {
      type: "object",
      properties: {
        module:   { type: "string" },
        function: { type: "string" },
        params:   { type: "object", description: "key/value params" },
        method:   { type: "string", enum: ["GET", "POST"], default: "GET" },
      },
      required: ["module", "function"],
    },
    handler: (args) =>
      callUapi(args.module, args.function, args.params || {}, args.method || "GET"),
  },
];

const server = new Server(
  { name: "billsense-cpanel", version: "0.1.0" },
  { capabilities: { tools: {} } }
);

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: tools.map((t) => ({
    name: t.name,
    description: t.description,
    inputSchema: t.inputSchema,
  })),
}));

server.setRequestHandler(CallToolRequestSchema, async (req) => {
  const tool = tools.find((t) => t.name === req.params.name);
  if (!tool) throw new Error(`Unknown tool: ${req.params.name}`);
  try {
    const result = await tool.handler(req.params.arguments || {});
    return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
  } catch (err) {
    return {
      isError: true,
      content: [{ type: "text", text: `${tool.name} failed: ${err.message}` }],
    };
  }
});

await server.connect(new StdioServerTransport());
process.stderr.write(
  `billsense-cpanel-mcp ready (host=${HOST}:${PORT}, user=${USER || "<unset>"})\n`
);
