"""Render README preview images (1600x900) for iCarPlay.

Each page is reproduced in HTML from the real PageUi design tokens, brand
assets in res/drawable-nodpi and vector icons in res/drawable, then screenshotted
with Chromium. Usage: python3 scripts/render-preview.py
"""
import base64, pathlib, re, subprocess, json

ROOT = pathlib.Path(__file__).resolve().parents[1]
RES = ROOT/"app/src/main/res"

BG="#04050A"; CARD="#0A0E17"; CARD2="#101726"; TEXT="#F6F8FF"; MUTED="#8D9BB3"; BORDER="#223047"
BLUE="#00E5FF"; CYAN="#38F2FF"; GREEN="#63FFA6"; GOLD="#FFC14D"; RED="#FF3D7F"; PINK="#FF5FD2"; PURPLE="#9B6CFF"

def rgb(h):
    h=h.lstrip('#'); return tuple(int(h[i:i+2],16) for i in (0,2,4))
def hexs(t): return "#%02X%02X%02X"%tuple(max(0,min(255,int(round(v)))) for v in t)
def blend(base, accent, r):
    b=rgb(base); a=rgb(accent)
    return hexs([a[i]*r + b[i]*(1-r) for i in range(3)])

def data_uri(path):
    b=path.read_bytes()
    mime={".png":"image/png",".jpg":"image/jpeg",".jpeg":"image/jpeg",".svg":"image/svg+xml"}.get(path.suffix.lower(),"application/octet-stream")
    return f"data:{mime};base64,"+base64.b64encode(b).decode()

def vector_svg(name, color):
    """Android vector drawable -> inline svg string."""
    p = RES/"drawable"/f"{name}.xml"
    if not p.exists(): p = RES/"drawable"/f"{name}.xml"
    s = p.read_text(encoding="utf-8")
    vw = re.search(r'android:viewportWidth="([\d.]+)"', s).group(1)
    vh = re.search(r'android:viewportHeight="([\d.]+)"', s).group(1)
    out=[]
    for m in re.finditer(r"<path(.*?)/>", s, re.S):
        attrs=m.group(1)
        d=re.search(r'android:pathData="([^"]+)"', attrs).group(1)
        fill=re.search(r'android:fillColor="([^"]*)"', attrs)
        stroke=re.search(r'android:strokeColor="([^"]*)"', attrs)
        sw=re.search(r'android:strokeWidth="([\d.]+)"', attrs)
        sc=re.search(r'android:strokeLineCap="([^"]+)"', attrs)
        f = fill.group(1) if fill else "transparent"
        if f.startswith("@"): f="transparent"
        st = stroke.group(1) if stroke else None
        if st and st.startswith("@"): st=None
        style=f"fill:{color if f!='transparent' else 'transparent'};"
        if st or sw:
            style+=f"stroke:{color};stroke-width:{sw.group(1) if sw else 1.8};"
            style+=f"stroke-linecap:{sc.group(1) if sc else 'butt'};"
        out.append(f'<path d="{d}" style="{style}"/>')
    return (f'<svg viewBox="0 0 {vw} {vh}" xmlns="http://www.w3.org/2000/svg" '
            f'preserveAspectRatio="xMidYMid meet">{"".join(out)}</svg>')

BRANDS = {k: data_uri(RES/"drawable-nodpi"/f"ic_brand_{k}.png") for k in ("apple","baidu","android","huawei")}
ICONS = {k: vector_svg("ic_ui_"+k, "#FFFFFF") for k in ("airplay","carplay","carlife","auto","hicar")}

BASE_CSS = """
*{box-sizing:border-box;margin:0;padding:0}
html,body{width:1600px;height:900px;overflow:hidden}
body{background:linear-gradient(135deg,#04050A 0%,#06111D 24%,#090A18 52%,#14091C 78%,#04050A 100%);
     font-family:"Noto Sans CJK SC","Noto Sans CJK",sans-serif;color:__TEXT__;-webkit-font-smoothing:antialiased}
.page{width:1600px;height:900px;padding:12px 32px 10px;display:flex;flex-direction:column;gap:0}
.mono{font-family:"DejaVu Sans Mono",monospace;font-weight:700;letter-spacing:1px}
.header{position:relative;height:78px;display:flex;align-items:center;justify-content:center}
.title{font-size:40px;font-weight:800;letter-spacing:1px;
  background:linear-gradient(90deg,#9B6CFF,#38F2FF);-webkit-background-clip:text;background-clip:text;color:transparent;
  text-shadow:0 0 24px rgba(155,108,255,.35)}
.pill{font-size:17px;padding:5px 16px;border-radius:20px;border:1px solid __PURPLE_C__;
  background:__VER_BG__;color:__CYAN__}
.subtitle{text-align:center;color:__MUTED__;font-size:19px;margin-top:-6px}
.card{position:relative;border-radius:25px;padding:20px 22px;
  background:linear-gradient(180deg,var(--fa),var(--fb) 60%,#04050A)}
.card:before{content:"";position:absolute;inset:0;border-radius:inherit;padding:2px;
  background:var(--rim);-webkit-mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0);
  -webkit-mask-composite:xor;pointer-events:none}
.label{font-size:17px;font-weight:700;letter-spacing:1.4px}
.pill-sm{font-size:15px;font-weight:700;padding:4px 13px;border-radius:18px;border:1px solid;white-space:nowrap}
.metric{padding:5px 13px;border-radius:16px;background:__METRIC_BG__;border:1px solid __METRIC_BD__}
.metric .k{font-size:12.5px;color:#7C879C;letter-spacing:1px}
.metric .v{font-size:18px;font-weight:700;margin-top:3px}
.btn{flex:1;height:56px;border-radius:18px;display:flex;align-items:center;justify-content:center;
  font-size:24px;font-weight:700;color:__TEXT__;background:var(--bf);position:relative}
.btn:before{content:"";position:absolute;inset:0;border-radius:inherit;padding:2px;background:var(--brm);
  -webkit-mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0);-webkit-mask-composite:xor;pointer-events:none}
.back{margin-top:12px;height:64px;border-radius:20px;display:flex;align-items:center;justify-content:center;
  font-size:24px;font-weight:700;background:var(--bf);position:relative}
.back:before{content:"";position:absolute;inset:0;border-radius:inherit;padding:2px;background:var(--brm);
  -webkit-mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0);-webkit-mask-composite:xor;pointer-events:none}
.tool{height:58px;border-radius:17px;position:relative;flex:1;display:flex;align-items:center;justify-content:center;
  font-size:21px;font-weight:700;background:var(--bf)}
.tool:before{content:"";position:absolute;inset:0;border-radius:inherit;padding:2px;background:var(--brm);
  -webkit-mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0);-webkit-mask-composite:xor;pointer-events:none}
.act{height:48px;border-radius:15px;position:relative;flex:1;display:flex;align-items:center;justify-content:center;
  font-size:19px;font-weight:700;background:var(--bf)}
.act:before{content:"";position:absolute;inset:0;border-radius:inherit;padding:2px;background:var(--brm);
  -webkit-mask:linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0);-webkit-mask-composite:xor;pointer-events:none}
.ball{position:absolute;right:10px;bottom:20px;width:86px;height:86px;border-radius:50%;
  background:linear-gradient(135deg,#9B6CFF,#38F2FF);padding:3px;display:flex;align-items:center;justify-content:center}
.ball>div{width:100%%;height:100%%;border-radius:50%%;background:radial-gradient(circle at 35%% 30%%,#2A1D46,#0B0A1C 70%%);
  display:flex;align-items:center;justify-content:center}
"""
TOKENS = {
 "TEXT":TEXT,"MUTED":MUTED,"CYAN":CYAN,
 "PURPLE_C": blend(PURPLE, CYAN, .45),
 "VER_BG": blend(CARD, PURPLE, .16),
 "METRIC_BG": blend(BG, CYAN, .07),
 "METRIC_BD": blend(CARD, CYAN, .22),
}
CSS = BASE_CSS
for k,v in TOKENS.items():
    CSS = CSS.replace("__"+k+"__", v)

def rim(accent):
    c=[accent, blend(accent,CYAN,.55), blend(accent,GREEN,.35), blend(accent,GOLD,.35),
       blend(accent,PINK,.55), blend(accent,PURPLE,.55), accent]
    return "linear-gradient(130deg,"+",".join(c)+")"

def card(accent, body, extra=""):
    return (f'<div class="card" style="--fa:{blend(CARD2,accent,.17)};--fb:{blend(CARD,accent,.06)};'
            f'--rim:{rim(accent)};{extra}">{body}</div>')

def pill(label, accent):
    return (f'<span class="pill-sm" style="color:{accent};background:{blend(CARD,accent,.20)};'
            f'border-color:{blend(accent,TEXT,.18)}">{label}</span>')

def btn(label, accent):
    bf=f"linear-gradient(180deg,{blend(CARD2,accent,.30)},{blend(CARD,accent,.10)} 55%,#04050A)"
    return f'<div class="btn" style="--bf:{bf};--brm:{rim(accent)}">{label}</div>'

def back(label="←  返回主界面"):
    bf=f"linear-gradient(180deg,{blend(CARD2,BLUE,.20)},{blend(CARD,BLUE,.07)} 55%,#04050A)"
    return f'<div class="back" style="--bf:{bf};--brm:{rim(BLUE)}">{label}</div>'

def brand_logo(name, accent, size=122):
    return (f'<div style="width:{size}px;height:{size}px;background:{accent};'
            f'-webkit-mask:url({BRANDS[name]}) center/contain no-repeat;mask:url({BRANDS[name]}) center/contain no-repeat;'
            f'opacity:.96;filter:drop-shadow(0 0 18px {blend(accent,BG,.55)})"></div>')

def icon_badge(name, accent, size=64):
    return (f'<div style="width:{size}px;height:{size}px;border-radius:16px;display:flex;align-items:center;'
            f'justify-content:center;background:{blend(CARD2,accent,.22)};border:1px solid {blend(accent,TEXT,.12)}">'
            f'<div style="width:{int(size*.58)}px;height:{int(size*.58)}px">{ICONS[name].replace("#FFFFFF",accent)}</div></div>')

def ball():
    return ('<div class="ball"><div><svg viewBox="0 0 24 24" width="46" height="46" xmlns="http://www.w3.org/2000/svg">'
            '<path d="M4 14a8 8 0 0 1 16 0" fill="none" stroke="#38F2FF" stroke-width="1.6" stroke-linecap="round"/>'
            '<path d="M8 14a4 4 0 0 1 8 0" fill="none" stroke="#9B6CFF" stroke-width="1.6" stroke-linecap="round"/>'
            '<path d="M2.5 17.5h4l2-2.2h7l2 2.2h4v2.6q0 1.4-1.4 1.4H3.9q-1.4 0-1.4-1.4z" fill="none" stroke="#38F2FF" stroke-width="1.4"/>'
            '</svg></div></div>')

CARDSPECS = [
 ("AirPlay","镜像 · 音频",BLUE,"airplay","apple","待连接",BLUE),
 ("CarPlay","有线 / 无线",PURPLE,"carplay","apple","未配置",RED),
 ("CarLife+","无线 / 有线",GOLD,"carlife","baidu","待连接",GOLD),
 ("Android Auto","USB / 无线",GREEN,"auto","android","待连接",GREEN),
 ("Huawei HiCar","华为手机",RED,"hicar","huawei","待连接",RED),
]

def entry_card(title, sub, accent, icon, brand, state, state_accent):
    body = (
      f'<div style="display:flex;align-items:flex-start;justify-content:space-between">'
      f'{icon_badge(icon,accent)}<div style="margin-top:4px">{pill(state,state_accent)}</div></div>'
      f'<div style="flex:1;display:flex;align-items:center;justify-content:center">{brand_logo(brand,accent)}</div>'
      f'<div style="font-size:31px;font-weight:800;letter-spacing:1px">{title}</div>'
      f'<div style="color:{accent};font-size:18px;letter-spacing:1px;margin:3px 0 9px">{sub}</div>'
      f'<div style="width:62px;height:4px;border-radius:2px;background:linear-gradient(90deg,{BLUE},{CYAN},{GREEN},{GOLD},{PINK},{PURPLE})"></div>'
      f'<div style="margin-top:9px;font-size:18px;font-weight:700;color:{blend(accent,TEXT,.4)}">打开  →</div>'
    )
    return card(accent, body, "display:flex;flex-direction:column;flex:1;min-width:0")

def page_home():
    cards="".join(entry_card(*s) for s in CARDSPECS)
    chips = [("原车桌面", GREEN), ("悬浮窗已授予", GREEN), ("安全模式正常", GREEN), ("USB 未检测", MUTED)]
    chip_html="".join(f'<span style="color:{c};font-weight:700;margin-right:18px">● {t}</span>' for t,c in chips)
    status_body=(
      f'<div style="display:flex;align-items:center;font-size:17px;color:{MUTED};font-weight:700;letter-spacing:1.5px">'
      f'<span style="width:13px;height:13px;border-radius:50%;background:{GREEN};margin-right:12px;'
      f'box-shadow:0 0 12px {GREEN}"></span>车机状态'
      f'<span style="margin-left:auto;font-weight:400;color:{blend(MUTED,BG,.25)}">点状态可清除安全模式</span></div>'
      f'<div style="margin-top:10px;font-size:19px">{chip_html}</div>'
      f'<div style="margin-top:8px;font-size:19px;color:{TEXT}">HUAWEI BRA-AL00 · Android 12 (API 32)  ·  悬浮按钮运行中 · 无障碍</div>'
    )
    tools=[("悬浮按钮：开",GREEN),("连接中心",CYAN),("传文件",GOLD),("悬浮窗权限",PURPLE),("认证设置",GOLD),("桌面设置",BLUE)]
    tools_html="".join(btn(t,a) for t,a in tools).replace('class="btn"','class="tool"')
    return f"""<div class="page">
  <div class="header"><div class="title">iCarPlay</div>
    <div class="pill mono" style="position:absolute;right:0;top:50%;transform:translateY(-50%)">v0.9.4</div></div>
  <div style="display:flex;gap:14px;flex:1;min-height:0;margin-top:4px">{cards}</div>
  <div style="margin-top:16px">{card(BLUE, status_body, "padding:16px 24px")}</div>
  <div style="display:flex;gap:12px;margin-top:14px;height:62px">{tools_html}</div>
  {back("←  返回车机主屏幕")}
  {ball()}
</div>"""

def page_network():
    metrics=[("Wi‑Fi / SSID","HomeSpot-5G",CYAN,False),("频段 / 速率","5GHz · 100 Mbps · -50 dBm",BLUE,False)]
    def metric_row(pairs):
        out=""
        for k,v,ac,mono in pairs:
            cls=' class="mono"' if mono else ''
            out+=(f'<div class="metric" style="flex:1"><div class="k">{k}</div>'
                  f'<div class="v{cls}" style="color:{ac}">{v}</div></div>')
        return f'<div style="display:flex;gap:12px;margin-top:9px">{out}</div>'
    rows = (metric_row([("Wi‑Fi / SSID","HomeSpot-5G",CYAN,False),("频段 / 速率","5GHz · 100 Mbps · -50 dBm",BLUE,False)])
          + metric_row([("本机 IP","192.168.x.213",CYAN,True),("网关","192.168.x.1",CYAN,True)])
          + metric_row([("DNS","192.168.x.1 / —",MUTED,True),("热点 / Wi‑Fi Direct","关闭 · P2P 支持",GOLD,False)])
          + metric_row([("AirPlay 接收","运行中",GREEN,False),("本机端口","7000 待检",MUTED,True)]))
    caps="".join(pill(t,c) for t,c in [("客户端 开",BLUE),("自建热点 可试",PURPLE),("P2P 支持",CYAN),("5GHz 支持",GOLD)])
    net_body=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{BLUE}">网络状态</span>'
      f'<span style="margin-left:auto">{pill("已连接",GREEN)}</span></div>{rows}'
      f'<div style="display:flex;gap:12px;margin-top:10px">{caps}</div>')
    per_body=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{PURPLE}">外设状态</span></div>'
      f'<div style="margin-top:12px;display:flex;flex-direction:column;gap:9px">'
      f'<div><div style="font-size:15px;color:#7C879C">蓝牙</div><div style="font-size:20px;color:{RED};font-weight:700">关闭</div></div>'
      f'<div><div style="font-size:15px;color:#7C879C">HUAWEI HiCar</div><div style="font-size:20px;font-weight:700">系统未装 · 本应用入口</div></div>'
      f'<div><div style="font-size:15px;color:#7C879C">USB</div><div style="font-size:20px;font-weight:700">未检测到设备</div></div></div>'
      f'<div style="margin-top:auto;text-align:right;font-size:16px;font-weight:700;color:{CYAN}">点任一项看详情 · 连接补强 ›</div>')
    acts=[("打开 Wi‑Fi 设置",BLUE),("打开热点设置",GREEN),("mDNS 体检",GOLD),("网络体检",CYAN),
          ("开启车机热点",PURPLE),("停止车机热点",RED),("启动 AirPlay",BLUE),("停止 AirPlay",RED)]
    arows="".join(f'<div style="display:flex;gap:10px;margin-top:8px">'
                  f'{"".join(btn(t,a).replace("class=\"btn\"","class=\"act\"") for t,a in acts[i:i+4])}</div>' for i in (0,4))
    links="".join(f'<div style="flex:1;text-align:center;font-size:16px;font-weight:700;color:{c}">{t}</div>'
                  for t,c in [("一键无线投屏向导 ›",CYAN),("DLNA 投送接收 ›",PINK),("连接补强 ›",PURPLE),("无线传文件 ›",GOLD),("应用管理 ›",GREEN)])
    act_body=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{GREEN}">快捷操作</span>'
      f'<span style="margin-left:auto;font-size:16px;color:{MUTED}">状态自动刷新</span></div>{arows}'
      f'<div style="display:flex;margin-top:10px">{links}</div>')
    result_body=(f'<div style="font-size:17px;font-weight:700;color:{GOLD};letter-spacing:1.2px">体检结果</div>'
      f'<div style="margin-top:6px;font-size:17px;color:{MUTED}">“网络体检”查网关 / DNS / 外网 / AirPlay 端口；“mDNS 体检”查 iPhone 的 AirPlay / RAOP。</div>')
    return f"""<div class="page">
  <div class="header"><div class="title">连接中心</div>
    <div class="pill mono" style="position:absolute;right:0;top:50%;transform:translateY(-50%)">v0.9.4</div></div>
  <div class="subtitle" style="margin-top:-4px">Wi‑Fi / 蓝牙 / USB / 热点 · 网络体检与 AirPlay 自检共用</div>
  <div style="display:flex;gap:14px;margin-top:10px">
    <div style="flex:1.62">{card(BLUE, net_body)}</div>
    <div style="flex:1">{card(PURPLE, per_body, "display:flex;flex-direction:column")}</div>
  </div>
  <div style="margin-top:12px">{card(GREEN, act_body, "padding:14px 22px")}</div>
  <div style="margin-top:12px">{card(GOLD, result_body, "padding:12px 24px")}</div>
  {back()}
  {ball()}
</div>"""

PHOTO = data_uri(ROOT/"assets_test/photo.jpg")

def page_dlna():
    m1=(f'<div class="metric" style="flex:1"><div class="k">设备名</div>'
        f'<div class="v" style="color:{CYAN};font-size:19px">iCarPlay 车机 · f086</div></div>')
    m2=(f'<div class="metric" style="flex:.72"><div class="k">控制端口</div>'
        f'<div class="v mono" style="color:{CYAN}">8099</div></div>')
    m3=(f'<div class="metric" style="flex:.72"><div class="k">播放进度</div>'
        f'<div class="v" style="color:{GOLD}">静态图片</div></div>')
    head=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{GREEN}">接收状态</span>'
          f'<span style="margin-left:auto">{pill("播放中",GREEN)}</span></div>'
          f'<div style="margin-top:8px;font-size:30px;font-weight:800;color:{CYAN}">正在播放</div>'
          f'<div style="display:flex;gap:12px;margin-top:9px">{m1}{m2}{m3}</div>')
    stage=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{CYAN}">投送画面</span>'
           f'<span style="margin-left:auto">{pill("视频 / 图片",CYAN)}</span></div>'
           f'<div style="margin-top:10px;height:262px;border-radius:16px;border:1px solid {blend(CARD,CYAN,.2)};'
           f'background:#04050A;display:flex;align-items:center;justify-content:center;overflow:hidden">'
           f'<img src="{PHOTO}" style="height:100%;object-fit:contain"></div>')
    guide=(f'<div style="display:flex;align-items:center;font-size:19px;color:{GOLD};font-weight:700">'
           f'投送已连上：相册/视频 App 选“投屏 · Cast”换画面，音量用方向盘或车机音量键</div>')
    return f"""<div class="page">
  <div class="header"><div class="title">DLNA 投送接收</div>
    <div class="pill mono" style="position:absolute;right:0;top:50%;transform:translateY(-50%)">v0.9.4</div></div>
  <div class="subtitle" style="margin-top:-8px">安卓手机 / 电脑把音乐 · 视频 · 图片投送到车机 · 同一局域网</div>
  <div style="margin-top:14px">{card(GREEN, head)}</div>
  <div style="margin-top:16px">{card(CYAN, stage)}</div>
  <div style="margin-top:16px">{card(GOLD, guide, "padding:12px 24px")}</div>
  <div style="display:flex;gap:12px;margin-top:12px">{btn("启动接收",GREEN)}{btn("停止接收",RED)}{btn("复制地址",GOLD)}</div>
  {back("←  返回主界面")}
  {ball()}
</div>"""

def step_head(text, right, accent):
    return (f'<div style="display:flex;align-items:center"><span style="font-size:22px;font-weight:800;color:{TEXT}">{text}</span>'
            f'<span style="margin-left:auto">{pill(right,accent)}</span></div>')

def small_btn(label, accent):
    bf=f"linear-gradient(180deg,{blend(CARD2,accent,.22)},{blend(CARD,accent,.08)} 55%,#04050A)"
    return (f'<div style="flex:1;height:52px;border-radius:15px;display:flex;align-items:center;justify-content:center;'
            f'font-size:20px;font-weight:700;position:relative;background:{bf};border:1px solid {blend(accent,TEXT,.22)}">{label}</div>')

def page_guide():
    def metric4(pairs):
        return ('<div style="display:flex;gap:10px;margin-top:9px">'
                + "".join(f'<div class="metric" style="flex:1"><div class="k">{k}</div>'
                          f'<div class="v" style="color:{c};font-size:17px">{v}</div></div>'
                          for k,v,c in pairs) + '</div>')
    s1=(step_head("第 1 步 · 确认车机网络","当前链路",GREEN)
        + metric4([("当前 Wi‑Fi","HomeSpot-5G",CYAN),("本机 IP","192.168.x.213",BLUE),
                   ("频段","5GHz",GOLD),("网关","192.168.x.1",GREEN)])
        + '<div style="display:flex;gap:10px;margin-top:9px">'
        + small_btn("打开 Wi‑Fi 设置",BLUE) + small_btn("网络体检",CYAN) + '</div>')
    s2=(step_head("第 2 步 · 手机开热点（车机加入）","推荐",PURPLE)
        + f'<div style="margin-top:7px;font-size:17px;line-height:1.55;color:{MUTED}">'
          'iPhone：设置 → 个人热点 → 打开，顺手关掉“最大兼容性”更容易拿到 5GHz<br>'
          '安卓：设置 → 个人热点 → 打开，频段选 5GHz；车机连上后本页 Wi‑Fi 会变成热点名</div>'
        + '<div style="display:flex;gap:10px;margin-top:9px">'
        + small_btn("打开热点设置",PURPLE) + small_btn("连接补强",CYAN) + '</div>')
    s3=(step_head("第 3 步 · 打开对应接收页","看手机品牌选",CYAN)
        + '<div style="display:flex;gap:10px;margin-top:9px">'
        + small_btn("AirPlay（iPhone）",BLUE) + small_btn("HUAWEI HiCar",RED) + '</div>'
        + '<div style="display:flex;gap:10px;margin-top:8px">'
        + small_btn("CarLife+（安卓）",GREEN) + small_btn("DLNA 音视频",GOLD) + '</div>')
    note=(f'<div style="font-size:18px;font-weight:700;color:{GOLD};letter-spacing:1px">走不通时按这个顺序排查</div>'
          f'<div style="margin-top:5px;font-size:17px;line-height:1.5;color:{MUTED}">'
          '车机必须连上手机热点并拿到 IP 才算通；iPhone 投屏遇 NAT 隔离时用 DLNA 兜底；自建热点只作备选。</div>')
    return f"""<div class="page">
  <div class="header"><div class="title">一键无线投屏</div>
    <div class="pill mono" style="position:absolute;right:0;top:50%;transform:translateY(-50%)">v0.9.4</div></div>
  <div class="subtitle" style="margin-top:-10px">手机开热点 → 车机加入 → 打开接收页 · 没有外置路由器也能投</div>
  <div style="display:flex;flex-direction:column;gap:11px;margin-top:12px">
    {card(GREEN, s1, "padding:13px 20px")}
    {card(PURPLE, s2, "padding:13px 20px")}
    {card(CYAN, s3, "padding:13px 20px")}
    {card(GOLD, note, "padding:12px 20px")}
  </div>
  {back()}
  {ball()}
</div>"""


def kv(k, v, ac, mono=False):
    cls = ' class="mono"' if mono else ''
    return (f'<div class="metric" style="flex:1"><div class="k">{k}</div>'
            f'<div class="v{cls}" style="color:{ac}">{v}</div></div>')

def page_transfer():
    head=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{GOLD}">内置接收服务</span>'
          f'<span style="margin-left:auto">{pill("运行中",GREEN)}</span></div>'
          f'<div style="display:flex;gap:12px;margin-top:10px">'
          + kv("访问地址","192.168.x.213:8090",CYAN,True) + kv("已接收","1 个文件 · 27.6 MB",TEXT) + '</div>'
          f'<div style="display:flex;gap:10px;margin-top:12px">'
          + btn("启动服务",GREEN).replace('class="btn"','class="act"')
          + btn("停止服务",RED).replace('class="btn"','class="act"')
          + btn("复制地址",CYAN).replace('class="btn"','class="act"') + '</div>')
    ways="".join(f'<div style="display:flex;align-items:center;gap:10px;margin-top:9px;font-size:17px">'
                 f'<span style="color:{c};font-weight:700;width:86px">{t}</span>'
                 f'<span style="color:{MUTED};flex:1">{d}</span></div>'
                 for t,d,c in [("同 Wi‑Fi","车机和手机连同一个 Wi‑Fi，浏览器开地址即可",CYAN),
                               ("手机热点","手机开热点、车机加入，走 5GHz 更快",GREEN),
                               ("车机热点","车机自建热点，手机连车机（若系统允许）",GOLD),
                               ("蓝牙分享","仅发送：把车机上的 APK 分享给已配对手机",PURPLE)])
    ch_body=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{CYAN}">无线通道</span>'
             f'<span style="margin-left:auto">'
             + pill("只收 .apk",RED) + '</span></div>'
             + f'<div style="margin-top:8px">{ways}</div>')
    rows="".join(f'<div style="display:flex;align-items:center;gap:12px;margin-top:9px">'
                 f'<div style="flex:1"><div style="font-size:19px;font-weight:700">{n}</div>'
                 f'<div style="font-size:14px;color:{MUTED};margin-top:2px">{m} · {sz}</div></div>'
                 + btn(a,c).replace('class="btn"','class="act"') + '</div>'
                 for n,a,c,m,sz in [("icarplay.apk","安装",GREEN,"来自内置目录","27.6 MB"),
                                    ("icarplay-0.9.4.apk","蓝牙分享",PURPLE,"供手机下载","27.6 MB"),
                                    ("carlife-mod.apk","安装",GREEN,"手机上传","18.2 MB"),
                                    ("hicar-patch.apk","删除",RED,"手机上传","6.4 MB")])
    list_body=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{GOLD}">文件列表</span>'
               f'<span style="margin-left:auto;font-size:15px;color:{MUTED}">落盘 files/transfer/</span></div>'
               + f'<div style="display:flex;flex-direction:column">{rows}</div>')
    log_body=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{BLUE}">运行日志</span></div>'
              f'<div class="mono" style="margin-top:10px;font-size:14px;color:{MUTED};line-height:2">'
              f'[18:31:58] 服务启动 · 端口 8090 可用<br>'
              f'[18:32:04] 手机浏览器已连接 · 192.168.x.56<br>'
              f'[18:32:31] 上传 carlife-mod.apk · 18.2 MB · OK<br>'
              f'[18:32:33] 落盘 files/transfer/carlife-mod.apk</div>'
              f'<div style="margin-top:14px;font-size:13px;color:{MUTED}">只接受 .apk / .apks；单文件上限 260 MB；同名自动加 -2 后缀。</div>')
    return f"""<div class="page">
  <div class="header"><div class="title">无线传文件</div>
    <div class="pill mono" style="position:absolute;right:0;top:50%;transform:translateY(-50%)">v0.9.4</div></div>
  <div class="subtitle" style="margin-top:-4px">手机 ⇄ 车机互传 APK · 同一 Wi‑Fi / 手机热点 / 车机热点 · 蓝牙分享兜底</div>
  <div style="display:flex;gap:14px;margin-top:12px">
    <div style="flex:1.05">{card(GOLD, head)}</div>
    <div style="flex:1">{card(CYAN, ch_body)}</div>
  </div>
  <div style="display:flex;gap:14px;margin-top:12px;flex:1;min-height:0">
    <div style="flex:1.6;display:flex;flex-direction:column">{card(GREEN, list_body, "flex:1")}</div>
    <div style="flex:1;display:flex;flex-direction:column">{card(BLUE, log_body, "flex:1")}</div>
  </div>
  {back()}
  {ball()}
</div>"""

def page_appmanage():
    def app_row(name, pkg, badges, accent):
        pills="".join(pill(b,c) for b,c in badges)
        return (f'<div style="display:flex;align-items:center;gap:12px;margin-top:13px">'
                f'<div style="width:38px;height:38px;border-radius:11px;background:{blend(CARD2,accent,.24)};'
                f'border:1px solid {blend(accent,TEXT,.12)};display:flex;align-items:center;justify-content:center">'
                f'<div style="width:18px;height:18px;border-radius:5px;background:{accent}"></div></div>'
                f'<div style="flex:1;min-width:0"><div style="display:flex;align-items:center;gap:8px">'
                f'<span style="font-size:19px;font-weight:700;white-space:nowrap;flex-shrink:0">{name}</span>'
                f'<span style="display:flex;gap:6px;flex-wrap:nowrap">{pills}</span></div>'
                f'<div class="mono" style="font-size:13px;color:{MUTED};margin-top:2px">{pkg}</div></div>'
                + btn("卸载",RED).replace('class="btn"','class="act"')
                + btn("详情",CYAN).replace('class="btn"','class="act"') + '</div>')
    rows=(app_row("小白点","com.example.floatdot",[("无障碍服务",RED),("桌面有图标",GREEN)],RED)
          + app_row("百度 CarLife","com.baidu.carlife",[("系统预装",GOLD)],GOLD)
          + app_row("CarLife 接收端","com.baidu.carlife.receiver",[("桌面有图标",GREEN)],GREEN)
          + app_row("高德地图车机版","com.autonavi.amapauto",[("桌面有图标",GREEN)],BLUE)
          + app_row("旧版悬浮球","com.example.floatball",[("安装包缺失",RED)],RED)
          + app_row("iCarPlay","com.rockjesus.icarplay",[("当前应用",CYAN)],CYAN)
          + app_row("旧 CarLife 助手","com.baidu.carlife.helper",[("设备管理员",RED)],RED)
          + app_row("百度地图","com.baidu.BaiduMap",[("系统预装",GOLD)],GOLD))
    list_body=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{GREEN}">已安装的应用</span>'
               f'<span style="margin-left:auto">' + pill("含系统预装",GOLD) + '</span></div>'
               + f'<div style="display:flex;flex-direction:column">{rows}</div>'
               + f'<div style="margin-top:auto;padding-top:14px;display:flex;align-items:center;font-size:14px;color:{MUTED}">'
               f'<span style="flex:1">点「详情」闪退就跳过它，直接用「卸载」——卸载框不读应用详情页。</span>'
               f'<span style="color:{CYAN};font-weight:700">共 18 个 · 4 个可卸</span></div>')
    why="".join(f'<div style="display:flex;gap:9px;margin-top:9px"><span style="color:{c};font-weight:700;width:96px">{t}</span>'
                f'<span style="color:{MUTED};flex:1;font-size:15px">{d}</span></div>'
                for t,d,c in [("设备管理员","应用被设为管理员，先取消",RED),
                              ("无障碍服务","服务占用中，先关掉",PINK),
                              ("系统预装","只能停用，卸不掉",GOLD),
                              ("安装包缺失","系统有记录但文件没了，优先卸",RED)])
    shortcuts="".join(btn(t,c).replace('class="btn"','class="act"') for t,c in
                      [("无障碍设置",BLUE),("设备管理员",PURPLE),("应用列表",CYAN),("关于本页",GOLD)])
    why_body=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{RED}">卸不掉时</span></div>'
              + why + f'<div style="display:flex;gap:10px;margin-top:14px">{shortcuts}</div>')
    help_body=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{PURPLE}">权限边界</span></div>'
               f'<div style="margin-top:9px;font-size:15px;color:{MUTED};line-height:1.75">'
               f'卸载一定弹系统确认框；Android 不允许第三方应用静默删除别人的包，本页也不申请系统级权限。系统预装应用只能停用。</div>')
    adb_body=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{BLUE}">adb 兜底</span>'
              f'<span style="margin-left:auto;font-size:13px;color:{MUTED}">卸载框也闪退时</span></div>'
              f'<div class="mono" style="margin-top:9px;font-size:14px;color:{CYAN};line-height:1.9">'
              f'adb shell pm list packages | grep carlife<br>'
              f'adb shell pm uninstall --user 0 &lt;包名&gt;</div>')
    return f"""<div class="page">
  <div class="header"><div class="title">应用管理</div>
    <div class="pill mono" style="position:absolute;right:0;top:50%;transform:translateY(-50%)">v0.9.4</div></div>
  <div class="subtitle" style="margin-top:-4px">卸载车载多余的 App · 设置里点应用会闪退的，本页直接调卸载框</div>
  <div style="display:flex;gap:14px;margin-top:12px;flex:1;min-height:0">
    <div style="flex:1.5;display:flex;flex-direction:column">{card(GREEN, list_body, "display:flex;flex-direction:column;flex:1")}</div>
    <div style="flex:1;display:flex;flex-direction:column;gap:12px">
      <div style="display:flex;flex-direction:column">{card(RED, why_body)}</div>
      <div style="display:flex;flex-direction:column">{card(PURPLE, help_body)}</div>
      <div style="flex:1;display:flex;flex-direction:column">{card(BLUE, adb_body, "flex:1")}</div>
    </div>
  </div>
  {back()}
  {ball()}
</div>"""

def page_update():
    stats=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{CYAN}">当前版本</span>'
           f'<span class="mono" style="font-size:23px;font-weight:800;margin-left:14px">v0.9.4</span>'
           f'<span style="color:{MUTED};margin:0 10px">→</span>'
           f'<span class="mono" style="font-size:23px;font-weight:800;color:{GREEN}">v0.9.5</span>'
           f'<span style="margin-left:auto">' + pill("有新版本",GREEN) + '</span></div>')
    main_body=(stats
      + f'<div style="display:flex;gap:10px;margin-top:14px">'
      + btn("检查更新",CYAN).replace('class="btn"','class="act"')
      + f'<div style="flex:1.6">' + btn("一键下载并安装",GREEN).replace('class="btn"','class="act"') + '</div></div>'
      + f'<div style="margin-top:14px;height:12px;border-radius:6px;background:{blend(CARD2,CYAN,.10)};border:1px solid {blend(CARD,CYAN,.25)};overflow:hidden">'
        f'<div style="width:62%;height:100%;background:linear-gradient(90deg,{PURPLE},{CYAN},{GREEN})"></div></div>'
      + f'<div class="mono" style="margin-top:9px;font-size:15px;color:{MUTED}">下载源 1/2 · aka.doubaocdn.com · 第 1 次　17.1 MB / 27.6 MB</div>')
    notes=(f'<div style="display:flex;align-items:center"><span class="label" style="color:{PURPLE}">更新内容</span></div>'
           f'<div style="margin-top:10px;font-size:16px;color:{TEXT};line-height:1.75">'
           f'· 下载先走镜像，镜像全挂才回落 GitHub<br>'
           f'· 每个源最多 3 次，HTTP Range 断点续传<br>'
           f'· 界面显示当前「下载源 N/N · host · 第 N 次」<br>'
           f'· size + SHA-256 + 签名三重校验，通过才拉起系统安装<br>'
           f'· 全部源都失败时提示改用「无线传文件」，不硬装<br>'
           f'· 首页每 12 小时静默查一次，有新版本才点亮版本胶囊</div>'
           f'<div style="margin-top:16px;display:flex;align-items:center"><span class="label" style="color:{GOLD}">下载与校验</span></div>'
           f'<div style="display:flex;gap:12px;margin-top:10px">'
           + kv("声明大小","27,601,548 B",TEXT,True) + kv("SHA-256","d763375b… 已校验",GREEN,True) + '</div>'
           f'<div style="margin-top:14px;display:flex;align-items:center"><span class="label" style="color:{CYAN}">版本历史</span></div>'
           f'<div style="margin-top:8px;font-size:15px;color:{MUTED};line-height:1.7">'
           f'v0.9.3 一键更新　·　v0.9.2 应用管理增强　·　v0.9.1 应用管理　·　v0.9.0 无线传文件</div>')
    safe=(f'<div style="display:flex;align-items:center;gap:12px">'
          f'<div style="flex:1;font-size:15px;color:{MUTED}">安装由系统确认框完成；装不上先允许本应用「安装未知应用」。</div>'
          + btn("允许安装",GOLD).replace('class="btn"','class="act"')
          + btn("更新说明",PINK).replace('class="btn"','class="act"') + '</div>')
    return f"""<div class="page">
  <div class="header"><div class="title">版本更新</div>
    <div class="pill mono" style="position:absolute;right:0;top:50%;transform:translateY(-50%)">v0.9.4</div></div>
  <div class="subtitle" style="margin-top:-4px">一键检查并安装新版本 · 来源 GitHub Releases · 多源 + 断点续传 + 校验</div>
  <div style="margin-top:12px">{card(CYAN, main_body)}</div>
  <div style="display:flex;gap:14px;margin-top:12px;flex:1;min-height:0">
    <div style="flex:1.4;display:flex;flex-direction:column">{card(PURPLE, notes, "flex:1")}</div>
    <div style="flex:1;display:flex;flex-direction:column;gap:12px">
      <div style="flex:1;display:flex;flex-direction:column">{card(GOLD, f'<div style="display:flex;align-items:center"><span class="label" style="color:{GOLD}">安装权限</span></div>'
        + f'<div style="margin-top:10px;font-size:15px;color:{MUTED};line-height:1.7">安装由系统确认框完成；若提示打不开安装框，先允许本应用「安装未知应用」。</div>'
        + f'<div style="display:flex;gap:10px;margin-top:14px">' + btn("允许安装",GOLD).replace('class="btn"','class="act"')
        + btn("系统设置",PURPLE).replace('class="btn"','class="act"') + '</div>'
        + f'<div style="margin-top:14px;display:flex;gap:8px">'
        + pill("同签名才装",GREEN) + pill("不静默安装",CYAN) + pill("失败给原因",GOLD) + '</div>', "flex:1")}</div>
      <div style="flex:1;display:flex;flex-direction:column">{card(BLUE, f'<div style="display:flex;align-items:center"><span class="label" style="color:{BLUE}">安全边界</span></div>'
        + f'<div style="margin-top:10px;font-size:15px;color:{MUTED};line-height:1.75">只认本仓库 Release，且必须签名一致才装；不申请 INSTALL_PACKAGES，不做静默安装；更新只替换本应用自身，不动车机系统分区。</div>', "flex:1")}</div>
    </div>
  </div>
  {back()}
  {ball()}
</div>"""

PAGES = {"home":page_home(),"network":page_network(),"dlna":page_dlna(),"guide":page_guide(),"transfer":page_transfer(),"appmanage":page_appmanage(),"update":page_update()}

from playwright.sync_api import sync_playwright
OUT = ROOT/"docs/preview"; OUT.mkdir(exist_ok=True)
with sync_playwright() as p:
    b = p.chromium.launch(executable_path="/usr/local/bin/chromium", args=["--force-color-profile=srgb","--font-render-hinting=none"])
    pg = b.new_page(viewport={"width":1600,"height":900}, device_scale_factor=1)
    for name, body in PAGES.items():
        html = f"<html><head><meta charset='utf-8'><style>{CSS}</style></head><body>{body}</body></html>"
        pg.set_content(html)
        pg.wait_for_timeout(400)
        pg.screenshot(path=str(OUT/f"{name}.png"))
        print("rendered", name)
    b.close()
