<!--
 * 首页（百货化重构）：轮播 + 动态分类区块（前4个分类各一区，xmall 式左促销竖图+右商品网格）
 * 每个区块点击标题/更多可直达 /goods?categoryId=xx 全部商品页
 -->
<template>
  <div class="home" id="home" name="home">
    <!-- 轮播图 -->
    <div class="block">
      <el-carousel height="460px" :interval="4500" arrow="hover">
        <el-carousel-item v-for="item in carousel" :key="item.carouselId">
          <img class="carousel-img" :src="$target + item.imgPath" :alt="item.describes" />
        </el-carousel-item>
      </el-carousel>
    </div>

    <div class="main-box">
      <div class="main">
        <!-- 动态分类区块（百货 8 类取前 4） -->
        <div class="section" v-for="(sec, idx) in sections" :key="sec.categoryId">
          <div class="box-hd">
            <div class="title">{{ sec.categoryName }}</div>
            <div class="more">
              <router-link :to="{ path: '/goods', query: { categoryId: sec.categoryId } }">
                查看全部 <i class="el-icon-arrow-right"></i>
              </router-link>
            </div>
          </div>
          <div class="box-bd">
            <div class="promo-list">
              <router-link :to="{ path: '/goods', query: { categoryId: sec.categoryId } }">
                <img :src="$target + promoImgs[idx]" />
              </router-link>
            </div>
            <div class="list">
              <MyList :list="sec.list || []" :isMore="true"></MyList>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
// 前 4 个分类区块的促销竖图（与分类顺序对应）
const PROMO_IMGS = [
  "imgs/promo/cat-digital.svg",
  "imgs/promo/cat-home.svg",
  "imgs/promo/cat-beauty.svg",
  "imgs/promo/cat-food.svg"
];

export default {
  name: "HomeView",
  data() {
    return {
      carousel: "",
      sections: [],       // [{categoryId, categoryName, list:[]}]
      promoImgs: PROMO_IMGS
    };
  },
  created() {
    this.$axios
      .get("/api/resources/carousel")
      .then(res => (this.carousel = res.data.data || []))
      .catch(err => Promise.reject(err));

    // 动态拉分类 → 前 4 个分类各展示一个区块
    this.$axios
      .get("/api/category")
      .then(res => {
        const cats = (res.data.data || []).slice(0, 4);
        this.sections = cats.map(c => ({ ...c, list: [] }));
        cats.forEach((c, i) => this.loadCategory(c.categoryId, i));
      })
      .catch(err => Promise.reject(err));
  },
  methods: {
    loadCategory(categoryId, index) {
      this.$axios
        .get("/api/product/category/limit/" + categoryId)
        .then(res => {
          // Vue2 数组元素属性替换需 $set
          this.$set(this.sections, index, {
            ...this.sections[index],
            list: res.data.data || []
          });
        })
        .catch(() => {});
    }
  }
};
</script>
<style scoped>
@import "../assets/css/index.css";
/* P8 百货化：区块与促销竖图布局 */
.carousel-img { height: 460px; width: 100%; object-fit: cover; }
.box-hd { display: flex; justify-content: space-between; align-items: center; }
.box-hd .more a { font-size: 14px; color: #8c8ca6; transition: color .2s; }
.box-hd .more a:hover { color: #5b6ef5; }
.section { margin-bottom: 12px; }
.promo-list img { border-radius: 12px; transition: transform .3s, box-shadow .3s; cursor: pointer; }
.promo-list img:hover { transform: translateY(-3px); box-shadow: 0 10px 26px rgba(91,110,245,.18); }
</style>