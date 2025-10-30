# android-asset-webshell（基于 NanoHTTPD 的本地 HTTP 方案）

目的：在 Android 4.4.4 上稳定加载你的 H5 页面，避免 file:// 带来的“找不到节点/跳回主页/相对链接失效”等问题。

## 运行原理
- 启动 `LocalHttpServer` 将 `app/src/main/assets` 映射为 `http://127.0.0.1:12721/`。
- `WebView` 加载 `http://127.0.0.1:12721/index.html`（非 file://）。
- 服务器返回 `index.html` 时，强制在 `<script src="webjs.js">` 之前注入 `id-shim.js`（v3），确保 4.4.4 早期脚本也能通过全局变量名拿到 DOM 节点。
- 点击 `target=_blank` 或 `window.open()` 的链接，统一在当前 WebView 打开（`onPageFinished` 的脚本注入）。

## 使用步骤
1. 将你的完整 `index.html` 替换目录 `app/src/main/assets/index.html`（当前为占位页），其它依赖资源按相对路径放入 `assets`。
2. 如需保持最新的 `webjs.js`/`sw.js`，默认会从 `https://szzdmj.github.io/` 回源拉取；也可将文件放入 `assets` 覆盖本地优先。
3. 用 Android Studio 打开 `android-asset-webshell` 工程，连接设备（Android 4.4.4 及以上），运行 app。

## 常见问题
- 4.4.4 仍“找不到节点/转圈”：确认注入是否成功（`LocalHttpServer` 对 `index.html` 的 replace 生效），必要时在 `COMMON_IDS` 增加更早被访问的 id 名称。
- 点击某些“组合链接”无效：默认相对链接会基于 `http://127.0.0.1:12721/` 解析；若想改为补全到外网前缀，可在 `MainActivity.handleUrl()` 中按规则重写再 `loadUrl()`。
