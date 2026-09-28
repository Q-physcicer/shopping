-- =====================================================
-- V4 增量：数据百货化（星选百货）
-- 1) 清理小米全家桶商品与历史测试订单/购物车/收藏/支付流水
-- 2) 8 大百货分类 33 件商品（图片为本地 SVG：imgs/goods/*.svg）
-- 3) 新版轮播 banner（SVG）
-- 4) 新增"进行中"秒杀场次
-- 用户账号（admin/p1test/bkt*）保留
-- =====================================================
-- 连接字符集显式 utf8mb4（防客户端默认 latin1 导致中文乱码）
SET NAMES utf8mb4;

USE shopmanagement;

-- 列宽调整：title 短标语最长数据 14 字，加宽防个别行含特殊字符触发严格模式报错
ALTER TABLE product MODIFY product_title varchar(60) NOT NULL;

-- ---------- 清理 ----------
DELETE FROM `order`;
DELETE FROM payment_record;
DELETE FROM seckill_message_record;
DELETE FROM shopping_cart;
DELETE FROM collect;
DELETE FROM seckill_product;
DELETE FROM seckill_time;
DELETE FROM product;
DELETE FROM category;
DELETE FROM carousel;

-- ---------- 分类（8 大百货品类） ----------
INSERT INTO `category` (`category_id`, `category_name`) VALUES
(1, '数码家电'), (2, '家居日用'), (3, '美妆个护'), (4, '食品生鲜'),
(5, '服饰鞋包'), (6, '运动户外'), (7, '母婴玩具'), (8, '图书文具');

-- ---------- 商品（33 件，product_id 显式指定便于秒杀关联） ----------
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

-- ---------- 轮播 ----------
INSERT INTO `carousel` (`carousel_id`, `img_path`, `describes`) VALUES
(1, 'imgs/carousel/mall-open.svg', '新装开业大促'),
(2, 'imgs/carousel/food-fest.svg', '吃货嘉年华'),
(3, 'imgs/carousel/digital-life.svg', '智享数码周');

-- ---------- 秒杀：一个"进行中" + 一个"即将开始" ----------
INSERT INTO `seckill_time` (`time_id`, `start_time`, `end_time`) VALUES
(801, (UNIX_TIMESTAMP() - 1800) * 1000, (UNIX_TIMESTAMP() + 5400) * 1000),
(802, (UNIX_TIMESTAMP() + 3600) * 1000, (UNIX_TIMESTAMP() + 7200) * 1000);
INSERT INTO `seckill_product` (`seckill_id`, `product_id`, `seckill_price`, `seckill_stock`, `time_id`) VALUES
(901, 1,  149.0, 10, 801),
(902, 16,  69.0, 20, 801),
(903, 24,  59.0, 10, 801),
(904, 5, 999.0,  5, 802),
(905, 23, 129.0, 15, 802);

-- ---------- 验收 ----------
SELECT (SELECT COUNT(*) FROM category) AS categories,
       (SELECT COUNT(*) FROM product) AS products,
       (SELECT COUNT(*) FROM carousel) AS carousels,
       (SELECT COUNT(*) FROM seckill_product) AS seckills;