# -*- coding: utf-8 -*-
"""收一版版式：连接中心用文字链代替第三排按钮；向导页压缩到一屏"""
import io

NC = "app/src/main/java/com/rockjesus/icarplay/NetworkCenterActivity.java"
s = io.open(NC, encoding="utf-8").read()

OLD = '''        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams row3lp = PageUi.matchWrap(this);
        PageUi.margin(row3lp, this, 0, 5, 0, 0);
        row3.addView(action("一键无线投屏向导", PageUi.CYAN, v -> openWirelessGuide()), actionLp());
        row3.addView(action("DLNA 投送接收", PageUi.PINK, v -> openDlna()), actionLp());
        row3.addView(action("连接补强", PageUi.PURPLE, v -> openLinkBoost()), actionLp());
        actions.addView(row3, row3lp);
'''
NEW = '''        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        links.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams linksLp = PageUi.matchWrap(this);
        PageUi.margin(linksLp, this, 0, 3, 0, 0);
        links.addView(link("一键无线投屏向导 ›", PageUi.CYAN, v -> openWirelessGuide()), weight());
        links.addView(link("DLNA 投送接收 ›", PageUi.PINK, v -> openDlna()), weight());
        links.addView(link("连接补强 ›", PageUi.PURPLE, v -> openLinkBoost()), weight());
        actions.addView(links, linksLp);
'''
assert OLD in s
s = s.replace(OLD, NEW, 1)

OLD_HINT = '''        mdnsView = PageUi.text(this,
                "点“网络体检”检查网关、DNS、外网与 AirPlay 端口；点“mDNS 体检”检查"
                        + " iPhone 的 AirPlay / RAOP 是否能被发现。",
                9.5f, PageUi.MUTED, false);'''
NEW_HINT = '''        mdnsView = PageUi.text(this,
                "“网络体检”查网关 / DNS / 外网 / AirPlay 端口；“mDNS 体检”查 iPhone 的 AirPlay / RAOP。",
                9.5f, PageUi.MUTED, false);'''
assert OLD_HINT in s
s = s.replace(OLD_HINT, NEW_HINT, 1)

ANCHOR = '''    private LinearLayout.LayoutParams actionLp() {'''
LINK_HELPER = '''    private TextView link(String label, int accent, View.OnClickListener click) {
        TextView view = PageUi.text(this, label, 8.5f, accent, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setOnClickListener(click);
        return view;
    }

    private LinearLayout.LayoutParams actionLp() {'''
assert ANCHOR in s
s = s.replace(ANCHOR, LINK_HELPER, 1)
io.open(NC, "w", encoding="utf-8").write(s)

# ---------- 向导页压缩 ----------
W = "app/src/main/java/com/rockjesus/icarplay/WirelessGuideActivity.java"
w = io.open(W, encoding="utf-8").read()


def rep(old, new):
    global w
    assert old in w, old[:60]
    w = w.replace(old, new, 1)


# 三个卡片更紧凑
rep('LinearLayout step1 = PageUi.card(this, PageUi.GREEN, 14, 9);',
    'LinearLayout step1 = PageUi.card(this, PageUi.GREEN, 12, 6);')
rep('LinearLayout step2 = PageUi.card(this, PageUi.PURPLE, 14, 9);',
    'LinearLayout step2 = PageUi.card(this, PageUi.PURPLE, 12, 6);')
rep('LinearLayout step3 = PageUi.card(this, PageUi.CYAN, 14, 9);',
    'LinearLayout step3 = PageUi.card(this, PageUi.CYAN, 12, 6);')
rep('PageUi.margin(s2lp, this, 0, 8, 0, 0);', 'PageUi.margin(s2lp, this, 0, 6, 0, 0);')
rep('PageUi.margin(s3lp, this, 0, 8, 0, 0);', 'PageUi.margin(s3lp, this, 0, 6, 0, 0);')
rep('PageUi.margin(nlp, this, 0, 8, 0, 0);', 'PageUi.margin(nlp, this, 0, 6, 0, 0);')

# 第 1 步：四格压成一行，去掉单独的自建热点行
rep('''        LinearLayout rowA = new LinearLayout(this);
        rowA.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowAlp = PageUi.matchWrap(this);
        PageUi.margin(rowAlp, this, 0, 4, 0, 0);
        ssidValue = metric(rowA, "当前 Wi‑Fi", "—", PageUi.CYAN);
        ipValue = metric(rowA, "本机 IP", "—", PageUi.BLUE);
        bandValue = metric(rowA, "频段", "—", PageUi.GOLD);
        step1.addView(rowA, rowAlp);

        LinearLayout rowB = new LinearLayout(this);
        rowB.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowBlp = PageUi.matchWrap(this);
        PageUi.margin(rowBlp, this, 0, 3, 0, 0);
        gatewayValue = metric(rowB, "网关", "—", PageUi.GREEN);
        apValue = metric(rowB, "自建热点能力", "—", PageUi.PURPLE);
        step1.addView(rowB, rowBlp);
''', '''        LinearLayout rowA = new LinearLayout(this);
        rowA.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowAlp = PageUi.matchWrap(this);
        PageUi.margin(rowAlp, this, 0, 3, 0, 0);
        ssidValue = metric(rowA, "当前 Wi‑Fi", "—", PageUi.CYAN);
        ipValue = metric(rowA, "本机 IP", "—", PageUi.BLUE);
        bandValue = metric(rowA, "频段", "—", PageUi.GOLD);
        gatewayValue = metric(rowA, "网关", "—", PageUi.GREEN);
        step1.addView(rowA, rowAlp);
        apValue = null;
''')
rep('PageUi.margin(s1blp, this, 0, 6, 0, 0);', 'PageUi.margin(s1blp, this, 0, 4, 0, 0);')

# 第 2 步：说明压成两行
rep('''        TextView tips = PageUi.text(this,
                "iPhone：设置 → 个人热点 → 打开；顺手关掉“最大兼容性”，更容易拿到 5GHz\\n"
                        + "安卓：设置 → 个人热点 / 热点共享 → 打开，频段选 5GHz 优先\\n"
                        + "车机侧：Wi‑Fi 连到手机热点，连上后本页“当前 Wi‑Fi”会变成手机热点名",
                10f, PageUi.MUTED, false);''',
    '''        TextView tips = PageUi.text(this,
                "iPhone：设置 → 个人热点 → 打开，顺手关掉“最大兼容性”更容易拿到 5GHz\\n"
                        + "安卓：设置 → 个人热点 → 打开，频段选 5GHz；车机连上后本页 Wi‑Fi 会变成热点名",
                9.5f, PageUi.MUTED, false);''')
rep('PageUi.margin(s2blp, this, 0, 6, 0, 0);', 'PageUi.margin(s2blp, this, 0, 4, 0, 0);')

# 第 3 步：按钮变矮
rep('PageUi.margin(sr1, this, 0, 5, 0, 0);', 'PageUi.margin(sr1, this, 0, 4, 0, 0);')
rep('PageUi.margin(sr2, this, 0, 5, 0, 0);', 'PageUi.margin(sr2, this, 0, 4, 0, 0);')

# 提示卡压成两行
rep('''        TextView note = PageUi.text(this,
                "1 车机必须连上手机热点，DHCP 拿到 IP 才算通\\n"
                        + "2 iPhone 投屏要求同一网段，热点下常见 NAT 隔离，可改用 DLNA 音频兜底\\n"
                        + "3 车机自建热点只作备选：能开就用，开不了就统一走手机热点",
                10f, PageUi.MUTED, false);''',
    '''        TextView note = PageUi.text(this,
                "车机必须连上手机热点并拿到 IP 才算通；iPhone 投屏遇 NAT 隔离时用 DLNA 兜底；"
                        + "自建热点只作备选。",
                9.5f, PageUi.MUTED, false);''')

# 按钮高度与字号
rep('''        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, PageUi.dp(this, 36), 1f);''',
    '''        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, PageUi.dp(this, 32), 1f);''')
rep('Button b = PageUi.button(this, label, 10.5f, accent);',
    'Button b = PageUi.button(this, label, 10f, accent);')
rep('PageUi.fixed(this, 40);', 'PageUi.fixed(this, 38);')

io.open(W, "w", encoding="utf-8").write(w)
print("layout tightened")
