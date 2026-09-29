-- =====================================================
-- 星选商城 · 全量初始化脚本（库名 shop）
-- 整合自历史增量 V1__baseline ~ V7__order_hardening（已归档 .trash/sql-legacy/）
-- 15 张表最终态 + 种子数据；DROP+CREATE，可重复执行（重跑即重建全部表）
-- 执行：mysql -u root -p --default-character-set=utf8mb4 < sql/shop.sql
-- =====================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS `shop` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `shop`;

-- =====================================================
-- 一、商品域（product 服务读写）
-- =====================================================

-- ----------------------------
-- 分类
-- ----------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
  `category_id` int NOT NULL AUTO_INCREMENT,
  `category_name` varchar(20) NOT NULL,
  PRIMARY KEY (`category_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

INSERT INTO `category` (`category_id`, `category_name`) VALUES
(1, '数码家电'), (2, '家居日用'), (3, '美妆个护'), (4, '食品生鲜'),
(5, '服饰鞋包'), (6, '运动户外'), (7, '母婴玩具'), (8, '图书文具');

-- ----------------------------
-- 商品
-- ----------------------------
DROP TABLE IF EXISTS `product`;
CREATE TABLE `product` (
  `product_id` int NOT NULL AUTO_INCREMENT,
  `product_name` varchar(100) NOT NULL,
  `category_id` int NOT NULL,
  `product_title` varchar(60) NOT NULL,
  `product_intro` text NOT NULL,
  `product_picture` varchar(200) NULL DEFAULT NULL,
  `product_price` double NOT NULL,
  `product_selling_price` double NOT NULL,
  `product_num` int NOT NULL,
  `product_sales` int NOT NULL,
  `version` int NULL DEFAULT 1,
  PRIMARY KEY (`product_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- 33 件百货商品（id 显式指定便于秒杀关联）
INSERT INTO `product` (`product_id`, `product_name`, `category_id`, `product_title`, `product_intro`, `product_picture`, `product_price`, `product_selling_price`, `product_num`, `product_sales`, `version`) VALUES
-- 数码家电
(1, '无线蓝牙耳机 Pro', 1, '主动降噪，澎湃音质', '旗舰级主动降噪 / 蓝牙5.3秒连 / 32小时超长续航 / IPX5防水 / 通勤运动两相宜', 'imgs/goods/earbuds-pro.svg', 299, 249, 88, 36, 1),
(2, '智能手环 S6', 1, '14天续航 全面监测', '1.47英寸彩屏 / 心率血氧睡眠监测 / 100+运动模式 / 14天超长续航 / 50米防水', 'imgs/goods/smartband-s6.svg', 199, 169, 120, 45, 1),
(3, '快充移动电源', 1, '10000mAh 22.5W', '10000mAh大容量 / 22.5W超级快充 / 双输入双输出 / 数显电量 / 适配主流手机', 'imgs/goods/powerbank-10k.svg', 129, 99, 200, 78, 1),
(4, '4K 显示器 27英寸', 1, ' 旗舰画质 办公神器', '27英寸 4K IPS / 99% sRGB广色域 / Type-C 65W反向充电 / 低蓝光不闪屏 / 三微边设计', 'imgs/goods/monitor-4k.svg', 1699, 1499, 30, 12, 1),
(5, '节能变频空调', 1, '新一级能效 安静省电', '新一级能效 / 一键防直吹 / 56℃高温自清洁 / 静音运行低至19分贝 / 全屋速冷暖', 'imgs/goods/air-conditioner.svg', 2599, 2299, 25, 15, 1),
(6, '大容量空气炸锅', 1, '5L大容量 无油低脂', '5L黄金容量 / 360°热风循环 / 无油低脂更健康 / 8大预设菜单 / 触控定时免看管', 'imgs/goods/airfryer-5l.svg', 499, 399, 60, 28, 1),
-- 家居日用
(7, '316不锈钢保温杯', 2, '316医用级 12小时保温', '316L医用级内胆 / 12小时长效保温 / 500毫升便携 / 一键弹盖 / 多色可选', 'imgs/goods/thermos-cup.svg', 99, 79, 300, 92, 1),
(8, '收纳五件套', 2, '一网打尽全屋收纳', '五件套组合 / 加厚无纺布承重强 / 透气防尘 / 可折叠收纳 / 衣物玩具杂物分类', 'imgs/goods/storage-set.svg', 129, 89, 150, 40, 1),
(9, '恒温电水壶 1.5L', 2, '恒温烹煮过滤一体', '1.5升大容量 / 24小时恒温 / 316不锈钢内胆 / 多段温控泡奶泡茶 / 防干烧保护', 'imgs/goods/kettle-15l.svg', 199, 159, 80, 21, 1),
(10, '天然乳胶枕', 2, '泰国进口 人体工学', '93%泰国天然乳胶 / 人体工学曲线 / 透气抗菌防螨 / 适合侧睡仰睡 / 五年质保', 'imgs/goods/latex-pillow.svg', 299, 219, 100, 33, 1),
(11, '清洁卫生套组', 2, '全域清洁一站配齐', '清洁五件套 / 强效去油污 / 植物成分不伤手 / 卫浴厨房通用 / 一套搞定全家清洁', 'imgs/goods/clean-set.svg', 69, 49, 260, 56, 1),
-- 美妆个护
(12, '氨基酸洁面乳', 3, '温和净透 舒缓不紧绷', '氨基酸温和配方 / 绵密泡沫深层清洁 / 敏感肌可用 / 控油保湿不紧绷 / 120克大容量', 'imgs/goods/facial-cleanser.svg', 89, 69, 180, 64, 1),
(13, '玻尿酸爽肤水', 3, '五重玻尿酸 深层补水', '五重玻尿酸分子 / 250毫升大瓶装 / 即刻补水舒缓干燥 / 湿敷护肤两用 / 清爽不黏腻', 'imgs/goods/toner-ha.svg', 159, 129, 140, 37, 1),
(14, '清爽防晒霜', 3, 'SPF50+ 防晒黑科技', 'SPF50+ PA++++ / 水感轻薄不假白 / 防水防汗12小时 / 敏感肌孕妇可用 / 户外通勤必备', 'imgs/goods/sunscreen-50.svg', 129, 99, 220, 70, 1),
(15, '丝绒口红礼盒', 3, '一盘3色 质感礼赠', '丝绒哑光质地 / 显白不拔干 / 三色礼盒装 / 顺滑好上色 / 生日节日送礼首选', 'imgs/goods/lipstick-gift.svg', 199, 169, 90, 25, 1),
-- 食品生鲜
(16, '每日坚果 30日装', 4, '科学配比 新鲜锁鲜', '六种坚果果干科学配比 / 30袋独立包装 / 每日一袋约25克 / 低温烘焙不油炸 / 新鲜锁仓直达', 'imgs/goods/daily-nuts.svg', 159, 129, 320, 88, 1),
(17, '有机纯牛奶 12盒', 4, '有机认证 3.6g乳蛋白', '有机认证牧场 / 每百毫升3.6克乳蛋白 / 120毫克原生高钙 / 250毫升×12盒 / 全家营养早餐', 'imgs/goods/organic-milk.svg', 69, 59, 400, 105, 1),
(18, '云南咖啡豆 1kg', 4, '高山阿拉比卡 现烘香气', '云南高山阿拉比卡 / 中深烘焙坚果风味 / 现磨现烘新鲜直达 / 1公斤大容量 / 手冲意式皆宜', 'imgs/goods/coffee-beans.svg', 128, 99, 160, 42, 1),
(19, '榴莲千层蛋糕', 4, '猫山王榴莲 冷链直达', '猫山王榴莲果肉 / 动物奶油不加一滴水 / 四层榴莲三层芝士 / 450克冷链直达 / 生鲜锁鲜', 'imgs/goods/durian-cake.svg', 99, 79, 120, 58, 1),
-- 服饰鞋包
(20, '重磅纯棉T恤', 5, '300g重棉 不透不过时', '300克重磅新疆棉 / 落肩宽松版型 / 无侧缝工艺不卷边 / 透气不透 / 黑白灰多色可选', 'imgs/goods/cotton-tshirt.svg', 129, 89, 280, 66, 1),
(21, '直筒休闲牛仔裤', 5, '四面弹力 舒适百搭', '四面弹力面料 / 直筒微阔版型 / 不褪色水洗工艺 / 显瘦遮肉 / 通勤休闲百搭', 'imgs/goods/denim-pants.svg', 199, 149, 130, 31, 1),
(22, '防晒皮肤衣', 5, 'UPF50+ 轻若无物', 'UPF50+专业防晒 / 仅重180克 / 透气排汗不闷热 / 收纳收成一个拳头大小 / 徒步骑行必备', 'imgs/goods/sun-jacket.svg', 159, 119, 170, 39, 1),
(23, '轻弹运动鞋', 5, '爆米花中底 一脚蹬', '爆米花高弹中底 / 透气飞织鞋面 / 超轻仅230克 / 一脚蹬免系带 / 慢跑通勤两相宜', 'imgs/goods/sneakers.svg', 299, 229, 110, 47, 1),
-- 运动户外
(24, 'TPE环保瑜伽垫', 6, '加厚防滑 高回弹', 'TPE环保材质 / 双面防滑纹理 / 高回弹护膝 / 附赠网袋背带 / 晨练普拉提通用', 'imgs/goods/yoga-mat.svg', 159, 119, 200, 51, 1),
(25, '可调节哑铃 20kg', 6, '一对顶一套 居家增肌', '20公斤可调节 / 包胶不伤地板 / 旋转卡扣快速换片 / 居家力量训练 / 男女生通用', 'imgs/goods/dumbbell-20.svg', 399, 299, 75, 18, 1),
(26, '速干运动毛巾', 6, '秒吸速干 拒绝湿黏', '超细纤维材质 / 吸水速干是普通毛巾3倍 / 轻便易携带 / 健身跑步游泳必备 / 挂环设计', 'imgs/goods/sport-towel.svg', 59, 39, 350, 83, 1),
(27, '双人露营帐篷', 6, '3秒速开 防雨防晒', '三秒速开自动弹展 / 210T防水涂层 / 银胶防晒涂层 / 双人空间宽敝 / 加固地钉防风', 'imgs/goods/camping-tent.svg', 459, 359, 45, 14, 1),
-- 母婴玩具
(28, '积木空间站 520粒', 7, '航天启蒙 益智拼搭', '520粒航天主题积木 / 空间站+火箭+探测器 / 环保ABS材质 / 圆润无毛刺 / 6岁+航天启蒙', 'imgs/goods/blocks-space.svg', 299, 239, 95, 29, 1),
(29, '纯棉婴儿连体衣', 7, 'A类纯棉 5件超值装', 'A类婴幼儿标准 / 100%精梳棉 / 裆部按扣方便换尿布 / 五件超值套装 / 0-18个月尺码全', 'imgs/goods/baby-clothes.svg', 169, 129, 160, 34, 1),
(30, '儿童百科绘本 8册', 7, '3-8岁科学启蒙', '八大主题科普绘本 / 加厚铜版纸不易撕 / 环保大豆油墨 / 有声伴读版 / 亲子共读礼物', 'imgs/goods/science-books.svg', 198, 158, 120, 26, 1),
-- 图书文具
(31, '双头马克笔 24色', 8, '手账涂鸦 生猛出片', '24色双头设计 / 细头勾线粗头涂色 / 速干不透纸 / 环保水性墨水 / 手账绘画两不误', 'imgs/goods/markers-24.svg', 49, 35, 420, 97, 1),
(32, '多袋拉链笔记本', 8, '可插袋 便利随记', 'A5通勤尺寸 / 内置多卡位收纳袋 / 拉链防丢设计 / 加厚道林纸书写顺滑 / 学生办公通用', 'imgs/goods/notebook-a5.svg', 39, 29, 380, 74, 1),
(33, '中性笔 20支装', 8, '0.5mm 顺滑不断墨', '0.5毫米中性笔 / 20支超值装 / 速干不蹭纸 / 握胶舒适久写不累 / 学生考试办公通用', 'imgs/goods/gel-pens-20.svg', 36, 26, 500, 112, 1);

-- ----------------------------
-- 商品详情图（百货化后由前端静态 SVG 承担，表保留供详情页扩展）
-- ----------------------------
DROP TABLE IF EXISTS `product_picture`;
CREATE TABLE `product_picture` (
  `id` int NOT NULL AUTO_INCREMENT,
  `product_id` int NOT NULL,
  `product_picture` varchar(200) NULL DEFAULT NULL,
  `intro` text NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- 轮播
-- ----------------------------
DROP TABLE IF EXISTS `carousel`;
CREATE TABLE `carousel` (
  `carousel_id` int NOT NULL AUTO_INCREMENT,
  `img_path` varchar(50) NULL DEFAULT NULL,
  `describes` varchar(50) NULL DEFAULT NULL,
  PRIMARY KEY (`carousel_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

INSERT INTO `carousel` (`carousel_id`, `img_path`, `describes`) VALUES
(1, 'imgs/carousel/mall-open.svg', '新装开业大促'),
(2, 'imgs/carousel/food-fest.svg', '吃货嘉年华'),
(3, 'imgs/carousel/digital-life.svg', '智享数码周');

-- ----------------------------
-- 秒杀场次（source：auto=每日15点定时重建会清；manual=管理端手动建不清）
-- ----------------------------
DROP TABLE IF EXISTS `seckill_time`;
CREATE TABLE `seckill_time` (
  `time_id` int NOT NULL AUTO_INCREMENT,
  `start_time` bigint NULL DEFAULT NULL,
  `end_time` bigint NULL DEFAULT NULL,
  `source` varchar(10) NOT NULL DEFAULT 'auto' COMMENT '场次来源 auto/manual',
  PRIMARY KEY (`time_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- 一个"进行中" + 一个"即将开始"（时间戳毫秒；动态取当前时刻）
INSERT INTO `seckill_time` (`time_id`, `start_time`, `end_time`, `source`) VALUES
(801, (UNIX_TIMESTAMP() - 1800) * 1000, (UNIX_TIMESTAMP() + 5400) * 1000, 'auto'),
(802, (UNIX_TIMESTAMP() + 3600) * 1000, (UNIX_TIMESTAMP() + 7200) * 1000, 'auto');

-- ----------------------------
-- 秒杀商品
-- ----------------------------
DROP TABLE IF EXISTS `seckill_product`;
CREATE TABLE `seckill_product` (
  `seckill_id` int NOT NULL AUTO_INCREMENT,
  `product_id` int NOT NULL,
  `seckill_price` double NULL DEFAULT NULL,
  `seckill_stock` int NULL DEFAULT NULL,
  `time_id` int NULL DEFAULT NULL,
  PRIMARY KEY (`seckill_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

INSERT INTO `seckill_product` (`seckill_id`, `product_id`, `seckill_price`, `seckill_stock`, `time_id`) VALUES
(901, 1,  149.0, 10, 801),
(902, 16,  69.0, 20, 801),
(903, 24,  59.0, 10, 801),
(904, 5,  999.0,  5, 802),
(905, 23, 129.0, 15, 802);

-- =====================================================
-- 二、用户域（user 服务读写）
-- =====================================================

-- ----------------------------
-- 用户（密码 MD5 兼容存量；登录时 BCrypt 透明升级）
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(20) NOT NULL,
  `password` varchar(72) NOT NULL COMMENT '密码(BCrypt,兼容历史MD5)',
  `user_phone_number` varchar(11) NULL DEFAULT NULL,
  `role` varchar(10) NOT NULL DEFAULT 'USER' COMMENT '角色 USER/ADMIN',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE KEY `username` (`username`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- 初始 20 个账号（created_at 靠 DEFAULT CURRENT_TIMESTAMP 填充）
-- admin/admin123（role=ADMIN，密码 MD5 首次登录自动升级 BCrypt）；其余为历史演示账号
INSERT INTO `user` (`user_id`, `username`, `password`, `user_phone_number`, `role`) VALUES
(1,  'zerowdd',        'c51f9b1ec11855186a670e089d55a712', NULL, 'USER'),
(2,  'testtest',       '05a671c66aefea124cc08b76ea6d30bb', NULL, 'USER'),
(3,  'a123456',        'dc483e80a7a0bd9ef71d8cf973673924', NULL, 'USER'),
(4,  'ddwddw',         '78c8b92f09f93f438b4146d345784724', NULL, 'USER'),
(5,  'bababa',         'dc483e80a7a0bd9ef71d8cf973673924', NULL, 'USER'),
(6,  'admin',          '0192023a7bbd73250516f069df18b500', NULL, 'ADMIN'),
(7,  'xiaohehaokeai',  '7248b7ace448685ea3e24160bf9cc85e', NULL, 'USER'),
(8,  'abcde',          'e80b5017098950fc58aad83c8c14978e', NULL, 'USER'),
(9,  'abcdef',         'e80b5017098950fc58aad83c8c14978e', NULL, 'USER'),
(10, 'duanjiangtao',   'b61dc55947a309e0530314cee03d31bd', NULL, 'USER'),
(11, 'zq789456',       'a32f8a465202f8a14ca514fbffdb4a14', NULL, 'USER'),
(12, 'CCY_CCy',        '5d93ceb70e2bf5daa84ec3d0cd2c731a', NULL, 'USER'),
(13, 'l123456',        'c57562653c783faeb8b6cd917ef258c1', NULL, 'USER'),
(14, 'yzw0323',        '03918c2b726cb53556608c9da759d45b', NULL, 'USER'),
(15, 'yanguo',         '03918c2b726cb53556608c9da759d45b', NULL, 'USER'),
(16, 'testtt',         'f55e23f49445a3cf708c19577f218a5b', NULL, 'USER'),
(17, 'jayjethava1',    '7762dc5d78cb8366208b0c05b6fb14ea', NULL, 'USER'),
(18, 'lh235762524001', '6cfa955a82ad67f24f8daaf0e6f6e09e', NULL, 'USER'),
(19, 'liyiming',       '31e09a2697bff307006cfd52cc202270', NULL, 'USER'),
(33, 'qwert',          '327bc4e22b649d47c4546a3ec93f376b', NULL, 'USER');

-- ----------------------------
-- 收货地址
-- ----------------------------
DROP TABLE IF EXISTS `user_address`;
CREATE TABLE `user_address` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL COMMENT '归属用户',
  `receiver_name` varchar(50) NOT NULL COMMENT '收货人姓名',
  `receiver_phone` varchar(20) NOT NULL COMMENT '收货人手机号',
  `province` varchar(20) NULL COMMENT '省',
  `city` varchar(20) NULL COMMENT '市',
  `district` varchar(20) NULL COMMENT '区/县',
  `detail_address` varchar(200) NOT NULL COMMENT '详细地址',
  `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认地址 0否 1是',
  `created_time` bigint NULL COMMENT '创建时间戳(ms)',
  `updated_time` bigint NULL COMMENT '更新时间戳(ms)',
  PRIMARY KEY (`id`),
  KEY `idx_ua_user` (`user_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '收货地址；归属 user 服务读写';

-- =====================================================
-- 三、购物车域（cart 服务读写）
-- =====================================================

-- ----------------------------
-- 购物车（UNIQUE 防并发加购竞态）
-- ----------------------------
DROP TABLE IF EXISTS `shopping_cart`;
CREATE TABLE `shopping_cart` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `product_id` int NOT NULL,
  `num` int NOT NULL,
  `version` int NULL DEFAULT 0,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_cart_user_product` (`user_id`, `product_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- 收藏（UNIQUE 修复历史"重复收藏删不净"）
-- ----------------------------
DROP TABLE IF EXISTS `collect`;
CREATE TABLE `collect` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `product_id` int NOT NULL,
  `collect_time` bigint NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_collect_user_product` (`user_id`, `product_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- =====================================================
-- 四、订单域（order 服务读写）
-- =====================================================

-- ----------------------------
-- 订单（状态机：0待支付 1已支付 2已取消 3已完成 4删除；收货信息随单快照）
-- ----------------------------
DROP TABLE IF EXISTS `order`;
CREATE TABLE `order` (
  `id` int NOT NULL AUTO_INCREMENT,
  `order_id` varchar(20) NOT NULL,
  `user_id` int NOT NULL,
  `product_id` int NOT NULL,
  `product_num` int NOT NULL,
  `product_price` double NOT NULL,
  `order_time` bigint NULL DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0待支付 1已支付 2已取消(超时/用户) 3已完成 4删除',
  `pay_time` bigint NULL COMMENT '支付时间戳(ms)',
  `seckill_id` int NULL COMMENT '秒杀活动ID(秒杀单才有)',
  `receiver_name` varchar(50) NULL COMMENT '收货人姓名快照',
  `receiver_phone` varchar(20) NULL COMMENT '收货人手机快照',
  `receiver_address` varchar(300) NULL COMMENT '收货地址快照',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_order_user` (`user_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_order_status` (`status`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- 秒杀本地消息表（product 抢购写 / order 消费 CAS 改状态）
-- ----------------------------
DROP TABLE IF EXISTS `seckill_message_record`;
CREATE TABLE `seckill_message_record` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `message_id`    varchar(64)  NOT NULL COMMENT '业务消息ID = seckillId:userId',
  `user_id`       varchar(20)  NOT NULL,
  `seckill_id`    varchar(20)  NOT NULL,
  `product_id`    varchar(20)  NULL,
  `status`        varchar(16)  NOT NULL DEFAULT 'SENT' COMMENT 'SENT/CONSUMED/FAILED/CANCELLED',
  `retry_count`   int          NOT NULL DEFAULT 0,
  `error_message` varchar(255) NULL,
  `created_at`    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_message` (`message_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '秒杀订单消息记录';

-- ----------------------------
-- 模拟支付流水
-- ----------------------------
DROP TABLE IF EXISTS `payment_record`;
CREATE TABLE `payment_record` (
  `id`          bigint       NOT NULL AUTO_INCREMENT,
  `pay_no`      varchar(32)  NOT NULL COMMENT '支付流水号',
  `order_id`    varchar(20)  NOT NULL,
  `user_id`     int          NOT NULL,
  `amount`      double       NOT NULL,
  `pay_channel` varchar(16)  NOT NULL DEFAULT 'MOCK' COMMENT 'MOCK',
  `status`      tinyint      NOT NULL DEFAULT 0 COMMENT '0发起 1成功 2失败',
  `created_at`  timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pay_no` (`pay_no`),
  KEY `idx_pay_order` (`order_id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '支付流水';

-- ----------------------------
-- 售后申请（仅退款）；防重复申请由应用层 FOR UPDATE 锁父订单行保证
-- ----------------------------
DROP TABLE IF EXISTS `aftersale_record`;
CREATE TABLE `aftersale_record` (
  `id`            bigint       NOT NULL AUTO_INCREMENT,
  `aftersale_id`  varchar(32)  NOT NULL COMMENT '售后单号（IdWorker 生成）',
  `order_id`      varchar(20)  NOT NULL COMMENT '订单号（order.order_id）',
  `order_row_id`  int          NOT NULL COMMENT '订单行ID（order.id）',
  `user_id`       int          NOT NULL,
  `product_id`    int          NOT NULL,
  `product_num`   int          NOT NULL DEFAULT 1 COMMENT '售后数量（申请时订单行快照）',
  `refund_amount` double       NOT NULL COMMENT '退款金额 = 行价格*数量（申请时快照）',
  `reason`        varchar(200) NOT NULL COMMENT '用户申请理由',
  `status`        tinyint      NOT NULL DEFAULT 0 COMMENT '0待处理 1同意(退款完成) 2拒绝',
  `reject_reason` varchar(200) NULL COMMENT '拒绝理由（拒绝时必填）',
  `apply_time`    bigint       NOT NULL COMMENT '申请时间戳(ms)',
  `handle_time`   bigint       NULL COMMENT '处理时间戳(ms)',
  `handler_id`    int          NULL COMMENT '处理管理员 userId',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_aftersale_id` (`aftersale_id`),
  KEY `idx_aftersale_user` (`user_id`),
  KEY `idx_aftersale_row` (`order_row_id`),
  KEY `idx_aftersale_status` (`status`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '售后申请（仅退款）；归属 order 服务读写';

-- ----------------------------
-- 用户消息中心
-- ----------------------------
DROP TABLE IF EXISTS `user_message`;
CREATE TABLE `user_message` (
  `id`           bigint       NOT NULL AUTO_INCREMENT,
  `user_id`      int          NOT NULL COMMENT '归属用户',
  `type`         varchar(20)  NOT NULL COMMENT '消息类型：order_paid / order_cancelled / aftersale',
  `title`        varchar(100) NOT NULL COMMENT '标题',
  `content`      varchar(500) NOT NULL COMMENT '内容',
  `is_read`      tinyint      NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  `created_time` bigint       NOT NULL COMMENT '创建时间戳(ms)',
  PRIMARY KEY (`id`),
  KEY `idx_um_user` (`user_id`, `id`)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户消息中心；归属 order 服务读写';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- 验收（期望：8/33/3/2/5/20/0）
-- =====================================================
-- SELECT
--   (SELECT COUNT(*) FROM category)             AS categories,
--   (SELECT COUNT(*) FROM product)              AS products,
--   (SELECT COUNT(*) FROM carousel)             AS carousels,
--   (SELECT COUNT(*) FROM seckill_time)         AS seckill_times,
--   (SELECT COUNT(*) FROM seckill_product)      AS seckills,
--   (SELECT COUNT(*) FROM `user`)               AS users,
--   (SELECT COUNT(*) FROM product_picture)      AS product_pictures;
