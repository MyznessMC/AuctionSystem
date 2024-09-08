package cc.nukkit.myzness.auction;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.PluginCommand;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.NukkitRunnable;
import cn.nukkit.utils.Config;
import me.onebone.economyapi.EconomyAPI;
import cn.nukkit.item.Item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Main extends PluginBase {
    private Map<String, Auction> auctions = new HashMap<>();
    private Config config;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        config = getConfig();

        // 注册命令
        registerCommands();

        this.getLogger().info("■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■");
        this.getLogger().info(" ");
        this.getLogger().info("Auction 拍卖");
        this.getLogger().info("author: 眠悠子Myzness");
        this.getLogger().info("这是一个免费插件，如果你花钱了，那你一定是被骗了");
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
                sender.sendMessage("§c请勿在控制台执行该指令!");
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
                default:
                    player.sendMessage("§c未知命令，请输入 /auction help 查看帮助!");
                    return true;
            }
        }
        return false;
    }

    // 显示帮助信息
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6===== 拍卖指令帮助 =====");
        sender.sendMessage("§a/auction sell <起拍价> <拍卖名> - 发起拍卖");
        sender.sendMessage("§a/auction join <拍卖名> <出价> - 参与拍卖");
        sender.sendMessage("§a/auction cancel <拍卖名> - 取消拍卖");
        sender.sendMessage("§a/auction help - 查看指令帮助");
        sender.sendMessage("§6========================");
    }

    private boolean handleSell(Player seller, String[] args) {
        if (args.length < 3) return false;

        // 检查卖家是否已有拍卖活动进行中
        if (activeSellers.contains(seller.getName())) {
            seller.sendMessage("你已经有一个拍卖活动正在进行，不能创建新的拍卖!");
            return true;
        }

        double startingPrice;
        try {
            startingPrice = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            seller.sendMessage("起拍价必须是一个数字！");
            return true;
        }

        String auctionName = args[2];

        if (auctions.containsKey(auctionName)) {
            seller.sendMessage("该拍卖活动已经存在!");
            return true;
        }

        // 获取卖家手上的物品
        Item heldItem = seller.getInventory().getItemInHand();
        if (heldItem == null || heldItem.getId() == 0) {
            seller.sendMessage("你没有持有物品进行拍卖!");
            return true;
        }
        seller.getInventory().remove(heldItem); // 移除卖家的拍卖物品

        // 创建新的拍卖活动
        Auction auction = new Auction(seller, startingPrice, auctionName, heldItem, this);
        auctions.put(auctionName, auction);

        // 将卖家加入 activeSellers 集合，标记为正在进行拍卖
        activeSellers.add(seller.getName());

        auction.start();
        seller.sendMessage("§a拍卖活动 §e" + auctionName + "§a 已创建，起拍价为 §e" + startingPrice + "§a!");
        return true;
    }

    private boolean handleJoin(Player bidder, String[] args) {
        if (args.length < 3) return false;

        String auctionName = args[1];
        double bidAmount;
        try {
            bidAmount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            bidder.sendMessage("§c出价必须是一个数字！");
            return true;
        }

        Auction auction = auctions.get(auctionName);
        if (auction == null) {
            bidder.sendMessage("§c该拍卖活动不存在!");
            return true;
        }

        auction.join(bidder.getName(), bidAmount);
        bidder.sendMessage("§a你已参与拍卖 §e" + auctionName + "§a，出价为 §e" + bidAmount + "§a!");
        return true;
    }

    private boolean handleCancel(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c请提供拍卖名!");
            return false;
        }

        String auctionName = args[1];
        Auction auction = auctions.get(auctionName);

        if (auction == null) {
            player.sendMessage("§c拍卖活动 §e" + auctionName + "§c 不存在!");
            return true;
        }

        // 确认当前玩家是否为拍卖发起者或具有管理员权限
        if (!auction.getSeller().equals(player.getName()) && !player.isOp() && !player.hasPermission("auction.cancel.admin")) {
            player.sendMessage("§c你没有权限取消此拍卖活动!");
            return true;
        }

        // 执行取消拍卖操作
        auction.cancel();

        // 从拍卖列表中移除
        auctions.remove(auctionName);

        player.sendMessage("§a拍卖活动 §e" + auctionName + "§a 已取消!");

        return true;
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
                    player.sendMessage("§c你不能参与自己发起的拍卖!");
                }
                return;
            }

            // 检查出价是否大于当前最高出价，并且不低于起拍价
            if (bid <= currentBid || bid < startingPrice) {
                // 出价低于当前最高价或者低于起拍价
                Player player = Server.getInstance().getPlayer(bidder);
                if (player != null) {
                    player.sendMessage("§c你的出价必须高于当前最高价且不低于起拍价！");
                }
                return;
            }

            // 检查出价者是否有足够的余额
            double balance = EconomyAPI.getInstance().myMoney(bidder);
            if (balance < bid) {
                Player player = Server.getInstance().getPlayer(bidder);
                if (player != null) {
                    player.sendMessage("§c你的余额不足以进行此出价！");
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
            Server.getInstance().broadcastMessage("§e" + bidder + "§a 以 §e" + bid + " §a的价格出价。");
        }



        public void endAuction(Main plugin) {
            broadcastTask.cancel(); // 停止广播

            if (currentBidder != null) {
                // 拍卖成功，物品转移给赢家
                Player winner = plugin.getServer().getPlayer(currentBidder);
                if (winner != null) {
                    winner.getInventory().addItem(auctionItem); // 将物品转给赢家
                    seller.sendMessage("§e" + currentBidder + "§a 赢得了你的拍卖!");
                    // 给卖家增加当前竞拍金额
                    EconomyAPI.getInstance().addMoney(seller, currentBid);
                } else {
                    seller.sendMessage("§c赢家不在服务器上!");
                }
            } else {
                // 拍卖未成交，物品返还给卖家
                seller.sendMessage("§c拍卖未成交!");
                seller.getInventory().addItem(auctionItem); // 将拍卖的物品返还给卖家
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
            // 获取 broadcast_content 模板
            String broadcastTemplate = plugin.config.getString("broadcast_content");

            // 计算剩余时间
            long currentTime = System.currentTimeMillis();
            long timeLeft = (endTime - currentTime) / 1000;  // 剩余时间，单位为秒

            // 替换模板中的占位符
            String broadcastMessage = broadcastTemplate
                    .replace("{player}", seller.getName())
                    .replace("{item}", auctionItem.getName())
                    .replace("{id}", String.valueOf(auctionItem.getId()))
                    .replace("{special}", String.valueOf(auctionItem.getDamage())) // damage 表示物品的特殊值
                    .replace("{price}", String.valueOf(startingPrice))
                    .replace("{name}", name)
                    .replace("{time}", String.valueOf(timeLeft));  // 添加剩余时间

            // 广播消息
            plugin.getServer().broadcastMessage(broadcastMessage);
        }


        public void start() {
        }
    }

    private static Main getInstance() {
        return null;
    }

    private Set<String> activeSellers = new HashSet<>();

}