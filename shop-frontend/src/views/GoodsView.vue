<!--
 * 全部商品页（xmall 风格重构）：左侧分类栏 + 排序条（综合/销量/价格）+ 商品网格 + 分页
 * 路由参数支持 categoryId（新）与 categoryID（兼容）
 -->
<template>
  <div class="goods" id="goods" name="goods">
    <!-- 面包屑 -->
    <div class="breadcrumb">
      <el-breadcrumb separator-class="el-icon-arrow-right">
        <el-breadcrumb-item to="/">首页</el-breadcrumb-item>
        <el-breadcrumb-item>全部商品</el-breadcrumb-item>
        <el-breadcrumb-item v-if="currentCategory">{{ currentCategory.categoryName }}</el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="layout">
      <!-- 左侧分类栏（xmall 风格） -->
      <aside class="side">
        <div class="side-title">全部分类</div>
        <ul>
          <li :class="{ active: activeId === 0 }" @click="chooseCategory(0)">
            <i class="el-icon-menu"></i> 全部商品
          </li>
          <li v-for="c in categoryList" :key="c.categoryId"
              :class="{ active: activeId === c.categoryId }" @click="chooseCategory(c.categoryId)">
            <i class="el-icon-collection-tag"></i> {{ c.categoryName }}
          </li>
        </ul>
        <div class="side-banner">
          <img src="imgs/promo/cat-digital.svg" alt="促销" />
        </div>
      </aside>

      <!-- 右侧主区 -->
      <div class="main">
        <!-- 排序条 -->
        <div class="sort-bar">
          <div class="sorts">
            <span :class="{ on: sortKey === 'default' }" @click="setSort('default')">综合</span>
            <span :class="{ on: sortKey === 'sales' }" @click="setSort('sales')">
              销量 <i :class="sortKey==='sales' ? 'el-icon-sort' : 'el-icon-bottom'"></i>
            </span>
            <span :class="{ on: sortKey === 'price' }" @click="setSort('price')">
              价格 <i :class="sortKey==='price' ? 'el-icon-sort' : 'el-icon-bottom'"></i>
            </span>
          </div>
          <div class="count" v-if="total">共 {{ total }} 件商品</div>
        </div>

        <!-- 商品网格 -->
        <div class="list">
          <MyList :list="sortedProduct" v-if="sortedProduct.length > 0"></MyList>
          <div v-else class="none-product">
            <i class="el-icon-goods"></i> 抱歉没有找到相关的商品，看看其他的吧
          </div>
        </div>

        <!-- 分页 -->
        <div class="pagination" v-if="total > pageSize">
          <el-pagination background layout="prev, pager, next"
            :page-size="pageSize" :total="total"
            :current-page.sync="currentPage" @current-change="currentChange" />
        </div>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  name: "GoodsView",
  data() {
    return {
      categoryList: [],          // 分类列表（不含"全部"）
      activeId: 0,               // 当前分类：0=全部
      product: [],
      total: 0,
      pageSize: 15,
      currentPage: 1,
      sortKey: "default",        // default | sales | price
      sortAsc: false,
      search: ""
    };
  },
  computed: {
    currentCategory() {
      return this.categoryList.find(c => c.categoryId === this.activeId);
    },
    // 本页内排序（商品量级小，本地排序即可获得即时交互）
    sortedProduct() {
      const list = [...(this.product || [])];
      if (this.sortKey === "sales") {
        list.sort((a, b) => (b.productSales || 0) - (a.productSales || 0));
      } else if (this.sortKey === "price") {
        list.sort((a, b) => (a.productSellingPrice || 0) - (b.productSellingPrice || 0));
      }
      return list;
    }
  },
  watch: {
    // 路由变化（首页"查看全部"/搜索进入）
    $route: {
      immediate: true,
      handler(val) {
        if (val.path !== "/goods") return;
        const cid = val.query.categoryId || val.query.categoryID;
        this.activeId = cid ? Number(Array.isArray(cid) ? cid[0] : cid) : 0;
        this.currentPage = 1;
        this.search = val.query.search || "";
        this.getData();
      }
    }
  },
  activated() {
    // keep-alive 场景兜底
    this.getData();
  },
  created() {
    this.$axios.get("/api/category")
      .then(res => (this.categoryList = res.data.data || []))
      .catch(() => {});
  },
  methods: {
    chooseCategory(id) {
      if (id === this.activeId) return;
      this.activeId = id;
      this.currentPage = 1;
      this.getData();
    },
    setSort(key) {
      this.sortKey = key;
    },
    currentChange(page) {
      this.currentPage = page;
      this.getData();
      this.backtop();
    },
    getData() {
      // 分页接口：/api/product/page/{page}/{size}/{categoryId(0=全部)}
      const catPart = this.activeId === 0 ? 0 : this.activeId;
      this.$axios
        .get(`/api/product/page/${this.currentPage}/${this.pageSize}/${catPart}`)
        .then(res => {
          this.product = res.data.data || [];
          this.total = res.data.total || 0;
        })
        .catch(() => {});
    },
    backtop() {
      const timer = setInterval(() => {
        const top = document.documentElement.scrollTop || document.body.scrollTop;
        const speed = Math.floor(-top / 5);
        document.documentElement.scrollTop = document.body.scrollTop = top + speed;
        if (top === 0) clearInterval(timer);
      }, 20);
    }
  }
};
</script>
<style scoped>
.goods { background-color: #f5f5f5; padding-bottom: 30px; }
/* 面包屑 */
.goods .breadcrumb { height: 50px; background-color: #fff; }
.goods .breadcrumb .el-breadcrumb {
  width: 1225px; line-height: 30px; font-size: 15px; margin: 0 auto;
}
/* xmall 风格左右布局 */
.goods .layout {
  width: 1225px; margin: 15px auto 0; display: flex; align-items: flex-start; gap: 15px;
}
/* 左侧分类栏 */
.goods .side {
  width: 200px; background: #fff; border-radius: 12px; padding: 12px 0; flex-shrink: 0;
  box-shadow: 0 2px 12px rgba(43, 43, 56, 0.04);
}
.side-title { font-size: 15px; font-weight: 700; padding: 6px 18px 10px; color: #2b2b38;
  border-bottom: 1px solid #f1f1f6; }
.side ul { list-style: none; margin: 0; padding: 6px 0; }
.side li {
  padding: 11px 18px; font-size: 14px; color: #6d6d80; cursor: pointer;
  transition: all .18s; display: flex; align-items: center; gap: 8px;
}
.side li i { font-size: 13px; }
.side li:hover { color: #5b6ef5; background: rgba(91, 110, 245, 0.06); }
.side li.active {
  color: #fff; background: linear-gradient(90deg, #5b6ef5, #8f6ef5);
  margin: 0 8px; border-radius: 8px; padding-left: 10px;
}
.side-banner { padding: 10px; }
.side-banner img { width: 100%; border-radius: 10px; }
/* 主区 */
.goods .main { flex: 1; min-width: 0; }
.sort-bar {
  background: #fff; border-radius: 12px; padding: 0 18px; height: 48px;
  display: flex; justify-content: space-between; align-items: center;
  box-shadow: 0 2px 12px rgba(43, 43, 56, 0.04); margin-bottom: 14px;
}
.sorts span {
  display: inline-flex; align-items: center; gap: 3px; font-size: 14px;
  color: #6d6d80; padding: 4px 14px; margin-right: 8px; border-radius: 14px;
  cursor: pointer; transition: all .18s;
}
.sorts span:hover { color: #5b6ef5; }
.sorts span.on { color: #fff; background: linear-gradient(90deg, #5b6ef5, #8f6ef5); }
.sort-bar .count { font-size: 13px; color: #9a9aae; }
/* 列表 */
.goods .list { min-height: 500px; }
.none-product {
  background: #fff; border-radius: 12px; padding: 80px 0; text-align: center;
  color: #9a9aae; font-size: 15px;
}
.none-product i { display: block; font-size: 46px; margin-bottom: 12px; color: #d5d5e2; }
.pagination { height: 60px; text-align: center; }
</style>