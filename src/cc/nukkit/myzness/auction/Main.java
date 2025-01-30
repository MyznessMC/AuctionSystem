package cc.nukkit.myzness.auction;

import cn.nukkit.Player;
import cn.nukkit.form.element.ElementInput;
import cn.nukkit.form.element.ElementLabel;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.PluginCommand;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.NukkitRunnable;
import cn.nukkit.utils.Config;
import me.onebone.economyapi.EconomyAPI;
import cn.nukkit.item.Item;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Main extends PluginBase {
    private Map<String, Auction> auctions = new HashMap<>();
    private Config config;
    private Config languageConfig; // 添加语言配置

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        config = getConfig();

        // 复制 Language.yml 和 Log.json 到配置文件夹
        saveResource("Language.yml", false);
        saveResource("Log.json", false);

        // 加载 Language.yml 文件
        languageConfig = new Config(new File(getDataFolder(), "Language.yml"), Config.YAML); // 添加语言配置加载

        // 注册命令
        registerCommands();

        this.getLogger().info("■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■");
        this.getLogger().info(" ");
        this.getLogger().info("AuctionSystem 拍卖系统");
        this.getLogger().info("author: 眠悠子Miyoz");
        this.getLogger().info("这是一个免费插件，如果你花钱了，那你一定是被骗了");
        this.getLogger().info("你可以访问MOT社区获取最新版插件:https://bbs.nukkit-mot.com");
        this.getLogger().info(" ");
        this.getLogger().info("■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■");
    }

    // 注册命令
    private void registerCommands() {
        // 注册 auction 命令
        PluginCommand<Main> auctionCommand = (PluginCommand<Main>) this.getCommand("auction");
        if (auctionCommand != null) {
            auctionCommand.setExecutor(this);  // 将 main 类设为命令的执行者
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("auction")) {
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                // 显示帮助信息
                showHelp(sender);
                return true;
            }

            // 检查是否为控制台发送的指令
            if (!(sender instanceof Player)) {
                sender.sendMessage(languageConfig.getString("console_error")); // 使用语言配置
                return true;
            }

            // 处理玩家命令
            Player player = (Player) sender;
            switch (args[0].toLowerCase()) {
                case "sell":
                    return handleSell(player, args);
                case "join":
                    return handleJoin(player, args);
                case "cancel":
                    return handleCancel(player, args);
                case "setting":
                    // 检查玩家是否为 OP
                    if (!player.isOp()) {
                        player.sendMessage(languageConfig.getString("no_permission")); // 使用语言配置
                        return true;
                    }
                    return handleSetting(player, args);
                case "reload": // 新增 reload 子命令
                    // 检查玩家是否为 OP
                    if (!player.isOp()) {
                        player.sendMessage(languageConfig.getString("no_permission")); // 使用语言配置
                        return true;
                    }
                    reload(); // 调用 reloadConfig 方法
                    player.sendMessage("§a" + languageConfig.getString("config_reloaded")); // 使用语言配置
                    return true;
                default:
                    player.sendMessage(languageConfig.getString("unknown_command")); // 使用语言配置
                    return true;
            }
        }
        return false;
    }

    // 添加 handleSetting 方法
    private boolean handleSetting(Player player, String[] args) {
        FormWindowCustom form = new FormWindowCustom(languageConfig.getString("setting_title"));
        // 添加组件
        form.addElement(new ElementInput(languageConfig.getString("setting_auction_time_title"), "60"));
        form.addElement(new ElementInput(languageConfig.getString("setting_broadcast_time_title"), "10"));
        form.addElement(new ElementInput(languageConfig.getString("setting_broadcast_content_title"), "§e{player}§a开启了一个拍卖!物品为§e{item} §b({id}:{special})§a，起拍价为§e {price}§a，输入 §e/auction join {name} <金额>§a 来参与吧，§c在 §e{time}§c 秒后拍卖就将结束了哦"));
        form.addElement(new ElementLabel(languageConfig.getString("setting_broadcast_tips")));
        // 设置提交操作
        form.addHandler(FormResponseHandler.withoutPlayer(ignored -> {
            if (form.wasClosed()) {
                return;
            }

            try {
                // 获取表单中的输入值
                String auctionTimeInput = form.getResponse().getInputResponse(0); // 获取 auction_time 输入框的值
                String broadcastTimeInput = form.getResponse().getInputResponse(1); // 获取 broadcast_time 输入框的值
                String broadcastContentInput = form.getResponse().getInputResponse(2); // 获取 broadcast_content 输入框的值

                int auctionTime = Integer.parseInt(auctionTimeInput);
                int broadcastTime = Integer.parseInt(broadcastTimeInput);

                // 修改配置文件中的 auction_time, broadcast_time 和 broadcast_content
                config.set("auction_time", auctionTime);
                config.set("broadcast_time", broadcastTime);
                config.set("broadcast_content", broadcastContentInput);

                // 保存配置文件
                config.save();

                // 提示指令执行者
                player.sendMessage("§a" + languageConfig.getString("settings_saved")); // 使用语言配置
            } catch (NumberFormatException e) {
                player.sendMessage("§c" + languageConfig.getString("invalid_number")); // 使用语言配置
            }
        }));
        // 显示表单给玩家
        player.showFormWindow(form);
        return true;
    }


    // 显示帮助信息
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6===== " + languageConfig.getString("help_title") + " =====");
        sender.sendMessage("§a/auction sell <price> <name> - " + languageConfig.getString("help_sell"));
        sender.sendMessage("§a/auction join <name> <price> - " + languageConfig.getString("help_join"));
        sender.sendMessage("§a/auction cancel <name> - " + languageConfig.getString("help_cancel"));
        sender.sendMessage("§a/auction setting - " + languageConfig.getString("help_setting"));
        sender.sendMessage("§a/auction reload - " + languageConfig.getString("help_reload"));
        sender.sendMessage("§a/auction help - " + languageConfig.getString("help_help"));
        sender.sendMessage("§6========================");
    }

    private boolean handleSell(Player seller, String[] args) {
        if (args.length < 3) return false;

        // 检查卖家是否已有拍卖活动进行中
        if (activeSellers.contains(seller.getName())) {
            seller.sendMessage(languageConfig.getString("already_active")); // 使用语言配置
            return true;
        }

        double startingPrice;
        try {
            startingPrice = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            seller.sendMessage(languageConfig.getString("invalid_price")); // 使用语言配置
            return true;
        }

        String auctionName = args[2];

        if (auctions.containsKey(auctionName)) {
            seller.sendMessage(languageConfig.getString("auction_exists")); // 使用语言配置
            return true;
        }

        // 获取卖家手上的物品
        Item heldItem = seller.getInventory().getItemInHand();
        if (heldItem == null || heldItem.getId() == 0) {
            seller.sendMessage(languageConfig.getString("no_item")); // 使用语言配置
            return true;
        }
        seller.getInventory().remove(heldItem); // 移除卖家的拍卖物品

        // 创建新的拍卖活动
        Auction auction = new Auction(seller, startingPrice, auctionName, heldItem, this);
        auctions.put(auctionName, auction);

        // 将卖家加入 activeSellers 集合，标记为正在进行拍卖
        activeSellers.add(seller.getName());

        auction.start();
        seller.sendMessage("§a" + languageConfig.getString("auction_created").replace("{auctionName}", auctionName).replace("{startingPrice}", String.valueOf(startingPrice))); // 使用语言配置
        return true;
    }

    private boolean handleJoin(Player bidder, String[] args) {
        if (args.length < 3) return false;

        String auctionName = args[1];
        double bidAmount;
        try {
            bidAmount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            bidder.sendMessage("§c" + languageConfig.getString("auction_must_number")); // 这里暂时保留，因为没有对应的语言配置
            return true;
        }

        Auction auction = auctions.get(auctionName);
        if (auction == null) {
            bidder.sendMessage("§c" + languageConfig.getString("auction_not_found").replace("{auctionName}", auctionName)); // 使用语言配置
            return true;
        }

        auction.join(bidder.getName(), bidAmount);
        bidder.sendMessage("§a" + languageConfig.getString("bid_placed").replace("{auctionName}", auctionName).replace("{bidAmount}", String.valueOf(bidAmount))); // 使用语言配置
        return true;
    }

    private boolean handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(languageConfig.getString("provide_auction_name")); // 使用语言配置
            return false;
        }

        String auctionName = args[1];
        Auction auction = auctions.get(auctionName);

        if (auction == null) {
            player.sendMessage("§c" + languageConfig.getString("auction_not_found").replace("{auctionName}", auctionName)); // 使用语言配置
            return true;
        }

        // 确认当前玩家是否为拍卖发起者或具有管理员权限
        if (!auction.getSeller().equals(player.getName()) && !player.isOp() && !player.hasPermission("auction.cancel.admin")) {
            player.sendMessage(languageConfig.getString("no_permission")); // 使用语言配置
            return true;
        }

        // 执行取消拍卖操作
        auction.cancel();

        // 从拍卖列表中移除
        auctions.remove(auctionName);

        player.sendMessage("§a" + languageConfig.getString("auction_cancelled").replace("{auctionName}", auctionName)); // 使用语言配置

        // 添加日志记录
        logTransaction(auctionName, auction.getSeller(), null, auction.getCurrentBid(), "Cancelled");

        return true;
    }

    // 添加日志记录方法
    private void logTransaction(String auctionName, String seller, String buyer, double price, String status) {
        try (FileWriter file = new FileWriter(getDataFolder() + File.separator + "Log.json", true)) {
            // 获取当前时间
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String timestamp = now.format(formatter);

            String logEntry = String.format("{\"auctionName\": \"%s\", \"seller\": \"%s\", \"buyer\": \"%s\", \"price\": %.2f, \"status\": \"%s\", \"timestamp\": \"%s\"},\n", auctionName, seller, buyer, price, status, timestamp);
            file.write(logEntry);
        } catch (IOException e) {
            getLogger().warning("Failed to write to Log.json: " + e.getMessage());
        }
    }

    private class Auction {
        private Player seller;
        private double startingPrice;
        private String name;
        private double currentBid;
        private String currentBidder;
        private long endTime;
        private Item auctionItem;
        private NukkitRunnable broadcastTask;
        private NukkitRunnable endAuctionTask;  // 定义拍卖结束任务
        private Main plugin;  // 保存传递的 Main 实例

        public Auction(Player seller, double startingPrice, String name, Item auctionItem, Main plugin) {
            this.seller = seller;
            this.startingPrice = startingPrice;
            this.name = name;
            this.currentBid = startingPrice;
            this.currentBidder = null;
            this.plugin = plugin;
            long auctionTime = plugin.config.getLong("auction_time");
            this.endTime = System.currentTimeMillis() + auctionTime * 1000;
            this.auctionItem = auctionItem.clone(); // 克隆物品，以防止直接修改

            // 广播拍卖信息的任务
            long broadcastTime = plugin.config.getLong("broadcast_time");
            broadcastTask = new NukkitRunnable() {
                @Override
                public void run() {
                    broadcastAuction(plugin);
                }
            };
            broadcastTask.runTaskTimer(plugin, 0, (int) (broadcastTime * 20)); // 定期广播

            // 处理拍卖结束的任务，并将其赋值给 endAuctionTask
            endAuctionTask = new NukkitRunnable() {
                @Override
                public void run() {
                    endAuction(plugin);
                }
            };
            endAuctionTask.runTaskLater(plugin, (int) auctionTime * 20);  // 定时结束拍卖
        }

        public String getSeller() {
            return seller.getName();
        }

        public void join(String bidder, double bid) {
            // 检查是否为拍卖发起者自己出价
            if (bidder.equals(seller.getName())) {
                Player player = Server.getInstance().getPlayer(bidder);
                if (player != null) {
                    player.sendMessage(languageConfig.getString("self_bid_error")); // 使用语言配置
                }
                return;
            }

            // 检查出价是否大于当前最高出价，并且不低于起拍价
            if (bid <= currentBid || bid < startingPrice) {
                // 出价低于当前最高价或者低于起拍价
                Player player = Server.getInstance().getPlayer(bidder);
                if (player != null) {
                    player.sendMessage(languageConfig.getString("bid_too_low")); // 使用语言配置
                }
                return;
            }

            // 检查出价者是否有足够的余额
            double balance = EconomyAPI.getInstance().myMoney(bidder);
            if (balance < bid) {
                Player player = Server.getInstance().getPlayer(bidder);
                if (player != null) {
                    player.sendMessage(languageConfig.getString("insufficient_balance")); // 使用语言配置
                }
                return;
            }

            // 更新出价
            if (currentBidder != null) {
                // 退还之前出价者的钱
                EconomyAPI.getInstance().addMoney(currentBidder, currentBid);
            }

            currentBidder = bidder;
            currentBid = bid;
            EconomyAPI.getInstance().reduceMoney(bidder, bid);

            // 广播当前出价情况
            Server.getInstance().broadcastMessage("§e" + languageConfig.getString("bid_broadcast").replace("{bidAmount}", String.valueOf(bid))); // 使用语言配置
        }



        public void endAuction(Main plugin) {
            broadcastTask.cancel(); // 停止广播

            if (currentBidder != null) {
                // 拍卖成功，物品转移给赢家
                Player winner = plugin.getServer().getPlayer(currentBidder);
                if (winner != null) {
                    winner.getInventory().addItem(auctionItem); // 将物品转给赢家
                    seller.sendMessage("§e" + currentBidder + "§a " + languageConfig.getString("auction_won")); // 使用语言配置
                    // 给卖家增加当前竞拍金额
                    EconomyAPI.getInstance().addMoney(seller, currentBid);

                    // 添加日志记录
                    logTransaction(name, seller.getName(), currentBidder, currentBid, "Successful");
                } else {
                    seller.sendMessage(languageConfig.getString("winner_offline")); // 使用语言配置

                    // 添加日志记录
                    logTransaction(name, seller.getName(), currentBidder, currentBid, "WinnerOffline");
                }
            } else {
                // 拍卖未成交，物品返还给卖家
                seller.sendMessage(languageConfig.getString("auction_unsuccessful")); // 使用语言配置
                seller.getInventory().addItem(auctionItem); // 将拍卖的物品返还给卖家

                // 添加日志记录
                logTransaction(name, seller.getName(), null, startingPrice, "Unsuccessful");
            }

            // 从拍卖列表中移除
            plugin.auctions.remove(name);
            plugin.activeSellers.remove(seller.getName()); // 从 activeSellers 中移除卖家
        }

        public void cancel() {
            // 停止广播任务
            if (broadcastTask != null) {
                broadcastTask.cancel();
            }

            // 取消拍卖结束的任务
            if (endAuctionTask != null) {
                endAuctionTask.cancel();  // 停止拍卖结束任务
            }

            // 恢复余额给卖家
            EconomyAPI.getInstance().addMoney(seller, startingPrice);

            // 如果已有出价者，也退还当前最高出价者的出价
            if (currentBidder != null) {
                EconomyAPI.getInstance().addMoney(currentBidder, currentBid);
            }

            // 将拍卖物品退还给卖家
            seller.getInventory().addItem(auctionItem);

            // 拍卖取消后，从 activeSellers 中移除卖家
            plugin.activeSellers.remove(seller.getName());
        }

        // 广播当前拍卖状态
        private void broadcastAuction(Main plugin) {
            String broadcastTemplate = plugin.config.getString("broadcast_content");

            long currentTime = System.currentTimeMillis();
            long timeLeft = (endTime - currentTime) / 1000;

            String broadcastMessage = broadcastTemplate
                    .replace("{player}", seller.getName())
                    .replace("{item}", auctionItem.getName())
                    .replace("{id}", String.valueOf(auctionItem.getId()))
                    .replace("{special}", String.valueOf(auctionItem.getDamage()))
                    .replace("{price}", String.valueOf(startingPrice))
                    .replace("{name}", name)
                    .replace("{time}", String.valueOf(timeLeft));

            plugin.getServer().broadcastMessage(broadcastMessage);
        }

        public double getCurrentBid() {
            return currentBid;
        }

        public void start() {
        }
    }

    private static Main getInstance() {
        return null;
    }

    private Set<String> activeSellers = new HashSet<>();

    private void reload() {
        saveDefaultConfig();
        config = getConfig();
        languageConfig = new Config(new File(getDataFolder(), "Language.yml"), Config.YAML); // 重新加载语言配置
    }
}