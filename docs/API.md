# 局域网家长管理 API · v0.11.0

电视监听 TCP **18765**，GET / 返回内置响应式 HTML，无外部依赖。仅允许回环或私网来源，同源 Origin、数字 IP/localhost Host；响应 no-store，禁止嵌入，请求体上限 16 KiB，socket 超时 4 秒，有界线程池。HTTP 仅用于可信家庭网络。

## 连接与家长验证

| 方法与路径 | 请求 / 响应 |
|---|---|
| GET /api/auth/status | 公开，返回 {configured:boolean,phraseConfigured:boolean}，不返回管理状态 |
| POST /api/pair | {code:"4位电视连接码"} 或 {qrToken:"64位十六进制秘密"} → {ticket}；**不是 token，不能访问管理 API** |
| POST /api/auth/setup | {ticket,pin,confirm,phrase,remember,label} → {token,expires,remember}；仅首次有效 |
| POST /api/auth/login | {pin,remember,label} → {token,expires,remember}；已有口令必须验证，无需再次扫码 |
| POST /api/auth/recover | {ticket,phrase,pin,confirm,remember,label} → 新授权；保留规则/记录，撤销旧浏览器 |

二维码为 http://电视IP:18765/#pair=<qrToken>。网页读取后清 fragment，再交换 ticket。4 位码与二维码共用一次机会，10 分钟失效，错误 5 次阻止 60 秒。电视展示时自动更新已用或过期的码。ticket 10 分钟，仅用于首次设置/恢复。PIN 为 4–12 位数字，沿用 PBKDF2 格式，错误尝试受限。

浏览器授权最多 16 个；记住固定 30 天，临时固定 2 小时，不滑动续期。电视私有偏好只存 token 哈希，并绑定当前 PIN 指纹。进程重启不撤销有效授权。设备时钟早于签发或超出到期时拒绝授权。

## 已登录接口

以下均需 Authorization: Bearer <token>：

| 方法与路径 | 行为 |
|---|---|
| GET /api/state | rules、用量、题链/限额/庆祝、统计、权限、设备、受管保护实际状态；不附带历史列表 |
| GET /api/history?review=0&page=0 | review 0=作答、1=错题；page 从 0 开始，每页 20 条，越界夹到末页；返回 items/page/pageCount/itemCount/pageSize 与统计 |
| PATCH /api/rules | 每次一个字段，原子合并，如 {grade:3}；非法/空/多字段拒绝 |
| PUT /api/rules | 完整替换规则；集成客户端使用，网页使用 PATCH |
| POST /api/lock | 保存并请求锁屏，实际结果查看 `interception`；没有悬浮窗权限时拒绝 |
| POST /api/unlock | 关守护并清题链/庆祝，不清历史 |
| GET /api/browsers | {items:[{id,label,issued,expires,remember,current}]}，不含 token/hash |
| POST /api/browsers/revoke | {id}，立即撤销（含当前浏览器） |
| POST /api/logout | 撤销当前浏览器 |
| POST /api/auth/change | {current,pin,confirm,remember,label}，验证原口令，撤销全部旧授权，返回当前浏览器新授权 |
| GET /api/auth/phrase | {configured:boolean}；不返回暗语原文 |
| POST /api/auth/phrase | {phrase}；已登录可设置/修改暗语，明文单次输入 |
| POST /api/protection | {action:"enable"/"disable"/"release"}；仅已配置设备所有者且系统支持时可用；失败返回实际错误 |

规则：enabled 为 boolean；intervalMinutes 1–120、dailyLimitMinutes 1–480、grade 1–6、semester 0/1/2、difficulty 1/2/3，数值为整数；subjects 非空不重复，取 math/chinese/english/poetry/riddle/science/ai；quizHint 去空白后 1–80 字。旧规则缺学期/难度/提示时沿用迁移默认。

`blocked` 表示规则要求拦截。查看 `interception` 可区分 `activity`（电视应用内的拦截页）、`overlay`（悬浮窗已添加）、`waiting`（等待）、`permission`（未授权）、`window_error`（创建失败并重试）和 `unavailable`（服务未运行）。悬浮窗已添加仍需实机验证 HDMI 等输入源是否可被遮挡。规则要求拦截却未显示遮挡时，亮屏用量继续累计。

`storageError` 为最近一次守护状态写入错误，无错误时为空。统计读取失败时状态仍会返回，`recordsUnavailable` 为 true，历史读取失败返回 503。protection 包含 deviceOwner/requested/uninstallBlocked/userControlDisabled/forceStopSupported/error。

400 为参数、口令、连接或设备能力不满足；401 为浏览器未登录/过期/撤销；403 为来源/Host 不符；404 为未知路径。断线不能认定保存成功，客户端提示并重连后读回状态。

v0.9.1 客户端等待：login/setup/recover/change 最多60秒，其他请求8秒。慢电视旧凭据首次验证后增加带盐快速校验值，接口请求/响应不变，不要求重新设置口令。页面显示处理中并防止重复点击；连接检查仅请求公开 auth/status，不验证口令、不增加失败次数。

v0.9.2 state 增加 remainingMillis（毫秒，服务器当前倒计时）；与现有 nextQuestionAtMillis/intervalAnchorMillis 并存。网页无需依赖手机/PC时钟与电视时钟一致；守护关闭、限额、答题与庆祝分别展示语义状态，不展示虚假倒计时。未改变持久化或计时规则。

v0.9.7 家长暗语：首尾空白忽略，6–32 Unicode码点，不允许控制字符。按用户授权明文保存于私有parent_recovery偏好，与数字PIN分开；变更暗语不撤销当前浏览器，使用暗语重设数字PIN会撤销全部旧浏览器并签发新授权。暗语5错限60秒，计数重启保留；旧8位恢复码接口与ADB重置广播已移除。新设置必须提供暗语；旧用户不强制迁移。返回与日志不含暗语。数字PIN哈希机制不变。
