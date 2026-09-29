<!--
 * @Description: 秒杀频道：场次 tabs + 秒杀商品列表
 -->
<template>
  <div class="goods" id="goods" name="goods">
    <!-- 面包屑 -->
    <div class="breadcrumb">
      <el-breadcrumb separator-class="el-icon-arrow-right">
        <el-breadcrumb-item to="/">首页</el-breadcrumb-item>
        <el-breadcrumb-item>秒杀</el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <!-- 面包屑END -->

    <!-- 时间场次标签 -->
    <div class="nav">
      <div class="product-nav">
        <div class="title">场次</div>
        <el-tabs v-model="activeName" type="card" v-if="Array.isArray(timeList) && timeList.length > 0">
          <el-tab-pane
            v-for="item in timeList"
            :key="item.timeId"
            :label="item.startTime | dateFormat"
            :name="'' + item.timeId"
          />
        </el-tabs>
      </div>
    </div>
    <!-- 时间场次标签END -->

    <!-- 主要内容区 -->
    <div class="main">
      <div class="list">
        <MySeckillList :list="product" v-if="product && product.length > 0"></MySeckillList>
        <div v-else class="none-product">该场次暂无秒杀商品，看看其他场次吧</div>
      </div>
    </div>
    <!-- 主要内容区END -->
  </div>
</template>
<script>
export default {
  data() {
    return {
      timeList: [],   // 时间段列表
      timeID: null, // 当前场次 ID
      product: [],    // 当前场次商品列表
      activeName: ""  // 当前选中的 tab（timeId 字符串）
    };
  },
  created() {
    this.getTime();
  },
  activated() {
    // keep-alive 再进入：恢复 URL 指定的场次
    if (this.$route.query.timeID != undefined && this.activeName !== "" + this.$route.query.timeID) {
      this.activeName = "" + this.$route.query.timeID;
      this.timeID = [Number(this.$route.query.timeID)];
      this.getData();
    }
  },
  watch: {
    // 点击场次 tab：切换商品
    activeName: function(val) {
      if (Number(val) > 0) {
        this.timeID = [Number(val)];
      }
      this.$router.push({
        path: "/seckill",
        query: { timeID: this.timeID }
      });
    },
    // 监听场次 id，响应相应的商品
    timeID: function() {
      this.getData();
    }
  },
  methods: {
    // 向后端请求场次列表
    getTime() {
      this.$axios
        .get("/api/seckill/product/time")
        .then(res => {
          this.timeList = res.data.data || [];
          // P2 修复：初始自动选中第一个场次 tab（原 activeName='-1'，无任何高亮，用户不知道看的是哪场）
          const urlTimeId = this.$route.query.timeID;
          if (urlTimeId != undefined) {
            this.activeName = "" + urlTimeId;
            this.timeID = [Number(urlTimeId)];
            this.getData();
          } else if (this.timeList.length > 0) {
            this.activeName = "" + this.timeList[0].timeId;
            this.timeID = [this.timeList[0].timeId];
            this.getData();
          }
        })
        .catch(() => {
          this.notifyError("网络异常，秒杀场次加载失败");
        });
    },
    // 向后端请求当前场次的秒杀商品
    getData() {
      if (!this.timeID || this.timeID.length === 0) return;
      this.$axios
        .get("/api/seckill/product/time/" + this.timeID[0])
        .then(res => {
          this.product = res.data.data || [];
        })
        .catch(() => {
          this.notifyError("网络异常，秒杀商品加载失败");
        });
    }
  }
};
</script>

<style scoped>
.goods {
  background-color: #f5f5f5;
}
/* 面包屑CSS */
.el-tabs--card .el-tabs__header {
  border-bottom: none;
}
.goods .breadcrumb {
  height: 50px;
  background-color: white;
}
.goods .breadcrumb .el-breadcrumb {
  width: 1225px;
  line-height: 30px;
  font-size: 16px;
  margin: 0 auto;
}
/* 面包屑CSS END */

/* 场次标签CSS */
.goods .nav {
  background-color: white;
}
.goods .nav .product-nav {
  width: 1225px;
  height: 40px;
  line-height: 40px;
  margin: 0 auto;
}
.nav .product-nav .title {
  width: 50px;
  font-size: 16px;
  font-weight: 700;
  float: left;
}
/* 场次标签CSS END */

/* 主要内容区CSS */
.goods .main {
  margin: 0 auto;
  max-width: 1225px;
}
.goods .main .list {
  min-height: 650px;
  padding-top: 14.5px;
  margin-left: -13.7px;
  overflow: auto;
}
.goods .main .none-product {
  color: #333;
  margin-left: 13.7px;
  padding: 60px 0;
}
/* 主要内容区CSS END */
</style>
