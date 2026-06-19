import collections
# Union of classes seen across the 3 security datasets
classes = [
 # ph-fake (50)
 '20','50','100','200','500','1k','false shadow thread','false 20','false 20 concealed value','false 50',
 'false 50 concealed value','false 50 security thread','false 100','false 100 security thread','false 100 concealed value',
 'false 200','false 200 concealed value','false 200 security thread','false 1k enhanced value panel','false 1k security thread',
 'false 1k concealed value','false 1k ovi patch','false 1k ovi','false 500','false 500 concealed value','false 500 ovi',
 'false 500 security thread','false see through mark','false 500 enhanced value panel','50 security thread',
 '500 enhanced value panel','500 concealed value','500 ovi','500 security thread','50 concealed value','100 security thread',
 '100 concealed value','false 1k','false watermark','1k  enhanced value panel','1k security thread','1k ovi','1k concealed value',
 '1k ovi patch','20 concealed value','1k','200 concealed value','shadow thread','watermark','200 security thread','see through mark',
 # false_718MB extras
 '1k watermark','500 watermark','1k new security thread','1k old security thread','1k old shadow thread','500 shadowthread',
 'false 500 watermark','false 1k watermark',
]

DENOM_TOKENS = {'20','50','100','200','500','1k','new','old'}
def canon_security(name):
    s = ' '.join(str(name).lower().split())
    s = s.replace('shadowthread','shadow thread')        # normalize "shadowthread"
    is_false = s.startswith('false') or ' false ' in (' '+s+' ')
    base = ' '.join(w for w in s.replace('false','').split() if w not in DENOM_TOKENS).strip()
    if   'watermark' in base: feat='watermark'
    elif 'see through' in base: feat='see_through_mark'
    elif 'shadow thread' in base: feat='shadow_thread'
    elif 'security thread' in base: feat='security_thread'
    elif 'concealed value' in base: feat='concealed_value'
    elif 'enhanced value panel' in base: feat='enhanced_value_panel'
    elif 'ovi' in base: feat='ovi'           # ovi + ovi patch
    elif base == '': feat='bill'
    else: return None
    return ('false_'+feat) if is_false else feat

mapped=collections.Counter(); unmapped=[]
for c in classes:
    m=canon_security(c)
    if m: mapped[m]+=1
    else: unmapped.append(c)
print("canonical classes:")
for k,v in sorted(mapped.items()): print("  %-28s <- %d source classes" % (k, v))
print("UNMAPPED:", unmapped if unmapped else "NONE")
