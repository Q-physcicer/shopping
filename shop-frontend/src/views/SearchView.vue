<template>
  <!-- 搜索页面（P3）：ES/MySQL 双引擎，后端返回带 <em> 高亮 -->
  <main class="search">
    <div class="container">
      <div class="search-header">
        <h2>
          搜索 “<span class="kw">{{ $route.query.keyword || keyword }}</span>”
          <small v-if="total > 0">共 {{ total }} 件商品<span v-if="engine" class="engine">（{{ engine === 'elasticsearch' ? 'Elasticsearch' : 'MySQL' }}）</span></small>
        </h2>
        <div class="filters">
          <el-select v-model="categoryId" placeholder="全部分类" size="small" clearable style="width:140px"
                     @change="loadData(1)">
            <el-option v-for="c in categories" :key="c.categoryId" :label="c.categoryName" :value="c.categoryId"/>
          </el-select>
          <el-radio-group v-model="sort" size="small" @change="loadData(1)">
            <el-radio-button label="relevance">综合</el-radio-button>
            <el-radio-button label="sales">销量</el-radio-button>
            <el-radio-button label="price">价格</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div v-if="loading" class="loading">
        <i class="el-icon-loading"></i> 正在搜索…
      </div>

      <div v-else-if="total === 0" class="empty">
        <p>没有找到相关商品</p>
        <p class="tip">换个关键词试试，例如「Redmi」「骁龙」「空调」</p>
      </div>

      <div v-else class="results">
        <div class="card" v-for="item in list" :key="item.productId"
             @click="toDetails(item.productId)">
          <div class="pic">
            <img :src="$target + item.productPicture" :alt="item.productName">
          </div>
          <div class="info">
            <!-- 高亮内容由后端注入 <em>，此处仅放开 em 标签 -->
            <h3 v-html="safeHighlight(item.productName)">
            </h3>
            <p class="title" v-html="safeHighlight(item.productTitle)"></p>
            <p class="intro" v-html="safeHighlight(item.productIntro)"></p>
            <p class="price">￥{{ item.productSellingPrice }}
              <del v-if="item.productPrice > item.productSellingPrice">￥{{ item.productPrice }}</del>
              <span class="sales">已售 {{ item.productSales }}</span>
            </p>
          </div>
        </div>
      </div>

      <div class="pager" v-if="total > size">
        <el-pagination background layout="prev, pager, next" :total="total" :page-size="size"
                       :current-page.sync="page" @current-change="loadData"/>
      </div>
    </div>
  </main>
</template>

<script>
export default {
  name: "SearchView",
  data() {
    return {
      keyword: "",
      list: [],
      total: 0,
      engine: "",
      page: 1,
      size: 20,
      sort: "relevance",
      categoryId: "",
      categories: [],
      loading: false
    };
  },
  watch: {
    // 顶栏再次搜索时复用本页面
    "$route.query.keyword"(val) {
      this.keyword = val || "";
      this.loadData(1);
    }
  },
  created() {
    this.keyword = this.$route.query.keyword || "";
    this.loadCategories();
    this.loadData(1);
  },
  methods: {
    loadData(page) {
      if (page) this.page = page;
      if (!this.keyword) return;
      this.loading = true;
      this.$axios
        .get("/api/product/search", {
          params: {
            keyword: this.keyword,
            page: this.page,
            size: this.size,
            sort: this.sort,
            categoryId: this.categoryId || undefined
          }
        })
        .then(res => {
          const d = res.data.data || {};
          this.list = d.list || [];
          this.total = d.total || 0;
          this.engine = d.engine || "";
        })
        .finally(() => {
          this.loading = false;
        });
    },
    loadCategories() {
      this.$axios.get("/api/category").then(res => {
        this.categories = res.data.data || [];
      });
    },
    toDetails(productId) {
      this.$router.push({ path: "/goods/details", query: { productID: productId } });
    },
    // 后端高亮只含 <em> 标签，此处做白名单清洗，避免 XSS
    safeHighlight(text) {
      if (!text) return "";
      return String(text).replace(/<em>/g, "[em]").replace(/<\/em>/g, "[/em]")
        .replace(/</g, "&lt;").replace(/>/g, "&gt;")
        .replace(/\[em\]/g, "<em>").replace(/\[\/em\]/g, "</em>");
    }
  }
};
</script>

<style scoped>
.search { background: #f6f7fb; min-height: 60vh; padding: 24px 0 48px; }
.container { width: 1225px; margin: 0 auto; }
.search-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 18px; }
.search-header h2 { font-size: 20px; font-weight: 600; color: #2b2b38; }
.search-header .kw { color: #5b6ef5; }
.search-header small { color: #999; font-size: 13px; margin-left: 8px; }
.engine { color: #b6b6c8; }
.results { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.card { display: flex; background: #fff; border-radius: 12px; padding: 16px; cursor: pointer;
  transition: box-shadow .2s, transform .2s; }
.card:hover { box-shadow: 0 8px 24px rgba(91,110,245,.12); transform: translateY(-2px); }
.pic { flex: 0 0 160px; }
.pic img { width: 160px; height: 160px; object-fit: contain; }
.info { flex: 1; margin-left: 18px; display: flex; flex-direction: column; }
.info h3 { font-size: 17px; color: #2b2b38; margin: 0 0 6px; }
.info .title { color: #6d6d80; font-size: 13px; margin: 0 0 8px; }
.info .intro { color: #9a9aae; font-size: 12px; line-height: 1.7; margin: 0 0 auto;
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.price { color: #ff5f5f; font-size: 18px; font-weight: 600; }
.price del { color: #b9b9c8; font-size: 13px; margin-left: 6px; font-weight: 400; }
.price .sales { float: right; color: #9a9aae; font-size: 12px; font-weight: 400; }
.loading, .empty { text-align: center; padding: 80px 0; color: #9a9aae; }
.empty p { margin: 6px 0; font-size: 16px; color: #6d6d80; }
.empty .tip { font-size: 13px; color: #b6b6c8; }
.pager { margin-top: 22px; text-align: center; }
/* 命中高亮（后端注入的 <em>） */
.results /deep/ em { color: #5b6ef5; font-style: normal; font-weight: 700; background: rgba(91,110,245,.08); padding: 0 2px; border-radius: 3px; }
</style>