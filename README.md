RankPlugin
一个为 Mohist 核心量身定制的轻量级 Rank 插件
无需 Vault、无需其他依赖，仅需一个 rank.yml 即可为朋友间的微型服务器提供简单的身份标识和聊天美化功能。

✨ 特性
零依赖 – 不依赖 Vault、PlaceholderAPI 或任何第三方插件

手动配置 – 通过 rank.yml 自由定义 Rank 的前缀、后缀和聊天格式

多彩样式 – 支持颜色代码（& 符号）、彩虹字和闪烁字（客户端需支持）

自动生成配置 – 首次启动自动生成 rank.yml 模板，方便你快速上手

专为 Mohist 优化 – 通过拦截聊天事件，修复原版 Mohist 下玩家名显示为 <user> 的问题

开源且轻量 – 整个插件仅几百 KB，代码清晰，方便二次修改

📦 安装
下载 RankPlugin-1.0.1.jar

将 jar 文件放入服务端的 plugins/ 文件夹

重启服务端（或使用 /reload，但建议重启）

插件会自动生成 plugins/RankPlugin/rank.yml 配置文件

按需编辑 rank.yml，然后执行 /rank reload 重载配置

⚙️ 配置文件 (rank.yml)
基本结构
yaml
# 聊天格式全局占位符：
# %1$s = 玩家名, %2$s = 消息内容
# 示例: "&4[&c管理员&4] &f%1$s&7: &f%2$s"

ranks:
  管理员:                     # Rank 名称（不能重复）
    prefix: '&4[&c管理员&4]'   # 前缀
    suffix: '&4'              # 后缀（置于玩家名之后）
    chat: '&4[&c管理员&4] &r%1$s&7: &f%2$s'  # 自定义聊天格式（覆盖全局）
    rainbow: false            # 是否启用彩虹字
    blink: false              # 是否启用闪烁效果
    blinkColor: '&6'          # 闪烁时的颜色（仅当 blink: true 时生效）

  普通玩家:
    prefix: '&7[玩家]'
    suffix: '&f'
    chat: '&7[玩家] &f%1$s&7: &f%2$s'
    rainbow: false
    blink: false
    blinkColor: '&6'

🎮 命令与权限
所有命令均需 OP 权限 或拥有 rankplugin.admin 权限节点。

命令	说明	权限
/rank give <玩家名> <Rank名>	为指定玩家设置 Rank	rankplugin.admin
/rank remove <玩家名>	移除指定玩家的 Rank	rankplugin.admin
/rank list	列出所有已定义的 Rank 名称	rankplugin.admin
/rank reload	重载 rank.yml 配置文件	rankplugin.admin
提示：若未配置权限插件，OP 玩家可直接使用以上命令。

⚠️ 已知问题与限制
Forge 模组聊天监听失效
由于插件通过 setCancelled(true) 拦截了原始聊天事件，因此所有依赖 Forge ServerChatEvent 的模组（如某些聊天指令模组）将无法监听到玩家的聊天内容。这是为了修复 Mohist 下玩家名显示异常而做出的必要牺牲。

彩虹/闪烁效果
这些效果依赖于客户端渲染支持（如安装了 OptiFine 或自定义字体模组），若客户端不支持，则仅显示普通颜色。

仅支持 Mohist 核心
本插件专为 Mohist 1.20.1 设计，在纯 Bukkit/Spigot 或 Paper 服务端上可能无法正常工作（因为不存在 <user> 问题），请勿用于其他核心。

🔧 开发者信息
主类：com.mohistmc.rankplugin.Main

API 版本：1.20

构建：无外部依赖，直接编译即可

🧠 开发备注：本插件的核心逻辑由 AI 辅助生成，作者进行了调试、适配和集成，所有代码均在指定环境中经过测试，确保稳定运行。

欢迎 Fork 并提交 Pull Request，但请保留原作者信息。

📄 许可证
本项目采用 MIT License 开源，你可以自由使用、修改、分发，但必须保留版权声明。

🙏 致谢
感谢 MohistMC 团队为混合端做出的努力

感谢所有在 Mohist 环境下奋斗的服务器管理员

🧠 特别感谢 AI 工具在代码生成和调试过程中提供的辅助支持，让这个轻量级插件能够快速落地。


