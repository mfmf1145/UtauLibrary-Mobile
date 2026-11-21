package com.utaumarket.mobile.ui.screens

/**
 * 免责声明的四种语言正文。条款类文本保持正式、克制的措辞。
 *
 * 修订记录：
 * - 2026-10-17 扩充为 8 节（性质、权利、直链、来源、隐私、第三方、风险、责任）。
 *   繁体由 opencc s2twp 从简体转出，并对个别术语做了人工校正。
 */
internal val DISCLAIMER: Map<String, Doc> = mapOf(
    "zh" to Doc(
        title = "使用前须知",
        subtitle = "UtauLibrary Mobile 为第三方社区工具，与 OpenUtau 及 OpenUtau Mobile 项目无从属、授权或背书关系。",
        accept = "我已阅读并同意",
        sections = listOf(
        Section(
            "一、性质与功能",
            "本程序仅提供资源索引与下载辅助，不提供、不托管、不再分发任何声库、插件或音素器文件。所有下载内容均直接取自其原始发布地址，本程序不设中转服务器，不缓存、不镜像、不修改任何资源内容。",
        ),
        Section(
            "二、权利归属",
            "各声库、插件、音素器及其附带素材（含示例音频、图像、说明文档）的著作权与配布条款均归各自权利人所有。本程序仅提供链接，不构成对任何使用方式的授权，亦不代表权利人的意见或立场。",
        ),
        Section(
            "三、配布条款与直链",
            "部分权利人在其配布条款中禁止直接链接或再配布。遇此类情形，请使用程序内的「官网」入口前往原始发布页面获取资源。使用前务必阅读该资源随附的说明文件与授权条款；条款与本地法律或本须知冲突时，以权利人条款为准。",
        ),
        Section(
            "四、链接来源与移除",
            "本程序收录的链接均取自公开可访问的发布页面，其可达性会随时间变化。权利人如不希望其资源被收录，可通过项目仓库通知开发者，经核实后将及时移除相关条目；本程序不主张对任何第三方内容的任何权利。",
        ),
        Section(
            "五、数据与隐私",
            "本程序不收集、不上传任何个人数据，不含统计与广告组件。目录访问仅限用户主动授权的路径；设置、令牌与下载记录均保存在本机应用私有目录，卸载即删除。下载过程直接连接资源发布服务器。",
        ),
        Section(
            "六、第三方组件",
            "界面配色与控件规格参考 stakira/OpenUtau（MIT）；图标取自 Microsoft Fluent UI System Icons（MIT）；解压依赖 Apache Commons Compress、junrar、XZ for Java、Commons IO；网络请求使用 OkHttp。各组件的许可与版权声明见随附的 THIRD_PARTY_NOTICES。",
        ),
        Section(
            "七、风险提示",
            "OpenUtau Mobile 本体目前稳定性有限，存在丢失工程数据的可能，请随时保存。安装会向已授权目录写入文件，同名目录可能被覆盖；作者停止配布、服务器限制访问或链接变更，均可能导致下载失败。请在操作前自行备份重要数据。",
        ),
        Section(
            "八、使用限制",
            "严禁将本程序（含其安装包、界面、文案与图标）用于任何形式的盈利或商业用途，包括但不限于：付费下载、捆绑销售、以本程序作为付费服务的赠品、在程序中插入广告或引流，以及未经许可的二次分发。本程序为免费提供的非官方社区工具，仅供个人学习与创作使用。如需商业合作，须事先取得开发者书面许可。",
        ),
        Section(
            "九、责任与条款变更",
            "本程序按「现状」提供，不对可用性、完整性或特定用途适用性作任何明示或默示担保。在法律允许的最大范围内，开发者不对因使用本程序产生的直接或间接损失承担责任。本须知可能随版本更新而修订，修订后在下次启动时重新提示。",
        ),
        ),
    ),
    "zh-Hant" to Doc(
        title = "使用前須知",
        subtitle = "UtauLibrary Mobile 為第三方社群工具，與 OpenUtau 及 OpenUtau Mobile 專案無從屬、授權或背書關係。",
        accept = "我已閱讀並同意",
        sections = listOf(
        Section(
            "一、性質與功能",
            "本程式僅提供資源索引與下載輔助，不提供、不託管、不再分發任何聲庫、外掛或音素器檔案。所有下載內容均直接取自其原始發布位址，本程式不設中轉伺服器，不快取、不鏡像、不修改任何資源內容。",
        ),
        Section(
            "二、權利歸屬",
            "各聲庫、外掛、音素器及其附帶素材（含示例音訊、圖像、說明文件）的著作權與配布條款均歸各自權利人所有。本程式僅提供連結，不構成對任何使用方式的授權，亦不代表權利人的意見或立場。",
        ),
        Section(
            "三、配布條款與直鏈",
            "部分權利人在其配布條款中禁止直接連結或再配布。遇此類情形，請使用程式內的「官網」入口前往原始釋出頁面獲取資源。使用前務必閱讀該資源隨附的說明檔案與授權條款；條款與本地法律或本須知衝突時，以權利人條款為準。",
        ),
        Section(
            "四、連結來源與移除",
            "本程式收錄的連結均取自公開可訪問的釋出頁面，其可達性會隨時間變化。權利人如不希望其資源被收錄，可透過專案倉庫通知開發者，經核實後將及時移除相關條目；本程式不主張對任何第三方內容的任何權利。",
        ),
        Section(
            "五、資料與隱私",
            "本程式不收集、不上傳任何個人資料，不含統計與廣告元件。目錄訪問僅限使用者主動授權的路徑；設定、令牌與下載記錄均儲存在本機應用私有目錄，解除安裝即刪除。下載過程直接連線資源釋出伺服器。",
        ),
        Section(
            "六、第三方元件",
            "介面配色與控制元件規格參考 stakira/OpenUtau（MIT）；圖示取自 Microsoft Fluent UI System Icons（MIT）；解壓依賴 Apache Commons Compress、junrar、XZ for Java、Commons IO；網路請求使用 OkHttp。各元件的許可與版權宣告見隨附的 THIRD_PARTY_NOTICES。",
        ),
        Section(
            "七、風險提示",
            "OpenUtau Mobile 本體目前穩定性有限，存在丟失工程資料的可能，請隨時儲存。安裝會向已授權目錄寫入檔案，同名目錄可能被覆蓋；作者停止配布、伺服器限制訪問或連結變更，均可能導致下載失敗。請在操作前自行備份重要資料。",
        ),
        Section(
            "八、使用限制",
            "嚴禁將本程式（含其安裝包、介面、文案與圖示）用於任何形式的盈利或商業用途，包括但不限於：付費下載、捆綁銷售、以本程式作為付費服務的贈品、在程式中插入廣告或引流，以及未經許可的二次分發。本程式為免費提供的非官方社群工具，僅供個人學習與創作使用。如需商業合作，須事先取得開發者書面許可。",
        ),
        Section(
            "九、責任與條款變更",
            "本程式按「現狀」提供，不對可用性、完整性或特定用途適用性作任何明示或默示擔保。在法律允許的最大範圍內，開發者不對因使用本程式產生的直接或間接損失承擔責任。本須知可能隨版本更新而修訂，修訂後在下次啟動時重新提示。",
        ),
        ),
    ),
    "en" to Doc(
        title = "Before you begin",
        subtitle = "UtauLibrary Mobile is a third-party community tool. It is not affiliated with, authorised by, or endorsed by the OpenUtau or OpenUtau Mobile projects.",
        accept = "I have read and agree",
        sections = listOf(
        Section(
            "1. Nature of the software",
            "This application provides resource indexing and download assistance only. It does not host, mirror, or redistribute any voicebank, plugin, or phonemizer file. Every download is retrieved directly from its original publication address; the application operates no relay server and does not modify any resource content.",
        ),
        Section(
            "2. Ownership",
            "Copyright and distribution terms for voicebanks, plugins, phonemizers and their bundled assets (sample audio, images, documentation) remain with their respective rights holders. The application provides links only; it grants no licence for any use and does not represent the views of any rights holder.",
        ),
        Section(
            "3. Distribution terms and direct links",
            "Some rights holders prohibit direct linking or redistribution in their terms. In such cases, use the Official site entry in this application to reach the original distribution page. Read the documentation and licence terms supplied with each resource before use. Where those terms conflict with this notice or with local law, the rights holder's terms prevail.",
        ),
        Section(
            "4. Sources and takedown",
            "All links are taken from publicly accessible distribution pages and their availability changes over time. Rights holders who do not wish their work to be indexed may notify the developer through the project repository; entries will be removed after verification. The application claims no rights in any third-party content.",
        ),
        Section(
            "5. Data and privacy",
            "The application collects and uploads no personal data and contains no analytics or advertising components. Folder access is limited to paths the user authorises. Settings, access tokens and download records are stored in the application's private storage on this device and are removed on uninstall. Downloads connect directly to the servers that publish the resources.",
        ),
        Section(
            "6. Third-party components",
            "Interface colours and control metrics are based on stakira/OpenUtau (MIT); icons come from Microsoft Fluent UI System Icons (MIT); extraction uses Apache Commons Compress, junrar, XZ for Java and Commons IO; networking uses OkHttp. Full licence and copyright notices are provided in THIRD_PARTY_NOTICES.",
        ),
        Section(
            "7. Risks",
            "OpenUtau Mobile itself is not yet fully stable and may lose project data; save frequently. Installation writes files into the folder you authorised, and a folder of the same name may be overwritten. Downloads may fail if an author withdraws a release, a server restricts access, or a link changes. Back up important data before proceeding.",
        ),
        Section(
            "8. Restrictions on use",
            "This application (including its APK, interface, copy and icons) must not be used for any profit-making or commercial purpose. This includes, without limitation: paid distribution, bundling with paid products, offering it as a gift or incentive for a paid service, inserting advertisements or referral links, and unauthorised redistribution. It is a free, unofficial community tool intended for personal study and creative work. Commercial use requires prior written permission from the developer.",
        ),
        Section(
            "9. Liability and changes",
            "The application is provided as is, without any express or implied warranty of availability, completeness, or fitness for a particular purpose. To the maximum extent permitted by law, the developer accepts no liability for direct or indirect loss arising from its use. This notice may be revised with new versions and will be shown again on the next launch after a revision.",
        ),
        ),
    ),
    "ja" to Doc(
        title = "ご利用の前に",
        subtitle = "UtauLibrary Mobile は第三者のコミュニティ製ツールであり、OpenUtau および OpenUtau Mobile の各プロジェクトと提携・許諾・推薦の関係にはありません。",
        accept = "内容を確認し、同意します",
        sections = listOf(
        Section(
            "1. 本アプリの性質",
            "本アプリはリソースの索引およびダウンロード補助のみを提供します。音源・プラグイン・音素器のファイルを保有、保管、再配布することはありません。ダウンロードはすべて公開されている配布元から直接取得するもので、中継サーバーを運用せず、リソースの内容を改変しません。",
        ),
        Section(
            "2. 権利の帰属",
            "音源・プラグイン・音素器および同梱物（サンプル音声、画像、説明書など）の著作権と配布規約は、それぞれの権利者に帰属します。本アプリはリンクを提示するのみであり、いかなる利用も許諾せず、権利者の見解を代弁するものでもありません。",
        ),
        Section(
            "3. 配布規約と直リンク",
            "権利者が配布規約で直リンクや再配布を禁止している場合、本アプリの「公式サイト」から配布ページへアクセスしてください。ご利用前に各リソースに同梱された説明書および利用規約を必ずお読みください。本規約と権利者の規約が抵触する場合は、権利者の規約が優先します。",
        ),
        Section(
            "4. リンクの出典と削除",
            "掲載しているリンクはすべて公開されている配布ページのもので、到達性は時間とともに変化します。掲載を希望されない権利者の方はプロジェクトのリポジトリから開発者までご連絡ください。確認のうえ速やかに削除します。本アプリは第三者のコンテンツについて何ら権利を主張しません。",
        ),
        Section(
            "5. データとプライバシー",
            "本アプリは個人情報を収集・送信せず、解析や広告の仕組みを含みません。フォルダへのアクセスは利用者が許可した範囲に限られます。設定、アクセストークン、ダウンロード記録は端末内のアプリ専用領域に保存され、アンインストールで削除されます。ダウンロードは配布元のサーバーへ直接接続します。",
        ),
        Section(
            "6. 第三者コンポーネント",
            "画面の配色とコントロールの寸法は stakira/OpenUtau（MIT）、アイコンは Microsoft Fluent UI System Icons（MIT）に基づきます。解凍には Apache Commons Compress、junrar、XZ for Java、Commons IO、通信には OkHttp を使用しています。各ライセンスと著作権表示は THIRD_PARTY_NOTICES に記載しています。",
        ),
        Section(
            "7. リスク",
            "OpenUtau Mobile 本体は未だ安定しておらず、プロジェクトデータを失う可能性があります。こまめに保存してください。インストールは許可されたフォルダへ書き込みを行い、同名フォルダが上書きされる場合があります。配布の終了、サーバー側の制限、リンクの変更によりダウンロードが失敗することがあります。重要なデータは事前に控えを取ってください。",
        ),
        Section(
            "8. 利用の制限",
            "本アプリ（APK・画面・文案・アイコンを含む）を、いかなる形態であれ営利目的または商用目的で使用することを固く禁じます。これには、有料配布、有料製品への同梱、有料サービスの特典としての提供、広告や誘導リンクの挿入、無断の再配布などが含まれますが、これらに限りません。本アプリは個人の学習と創作のために無償提供される非公式のコミュニティツールです。商用利用には開発者の事前の書面による許諾が必要です。",
        ),
        Section(
            "9. 責任と改定",
            "本アプリは現状のまま提供され、可用性、完全性、特定目的への適合性について明示・黙示を問わず保証しません。法律が許す最大限の範囲で、開発者は本アプリの利用に起因する直接的・間接的損害について責任を負いません。本規約は版の更新に伴い改定されることがあり、改定後の次回起動時に再確認をお願いします。",
        ),
        ),
    ),
)
