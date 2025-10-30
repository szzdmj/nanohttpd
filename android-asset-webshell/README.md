# android-asset-webshell（NanoHTTPD 本地 HTTP 方案）

- 将 `app/src/main/assets` 映射为 `http://127.0.0.1:12721/`，避免 `file://` 兼容问题。
- 返回 `index.html` 时，会在 `<script src="webjs.js">` 之前强制注入 `id-shim.js`（4.4.4 早期 id→window 兼容补丁）。
- 完全本地：不做任何外网回源；构建时 CI 强校验必须包含 `index.html`、`id-shim.js`、`webjs.js`、`sw.js`。

## 使用
1. 把你的完整 `index.html`、`webjs.js`、`sw.js` 放入 `app/src/main/assets/`。
2. Android Studio 打开 `android-asset-webshell`，运行在 Android 4.4.4/10 上验证。
3. CI 工作流位于 `.github/workflows/android.yml`，会在构建前检查上述 4 个文件是否存在。

## 链接与 `_blank`
- App 会在页面加载完成后注入降级脚本，将 `window.open/_blank` 统一在当前 WebView 打开，避免“跳回主页”。
- 相对链接会基于 `http://127.0.0.1:12721/` 解析；如需改为补到某域名，可在 `MainActivity.handleUrl()` 调整。
