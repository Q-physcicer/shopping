<!--
 * @Description: 我的收藏页面组件
 * @Author: hai-27
 * @Date: 2020-02-20 17:22:56
 * @LastEditors: hai-27
 * @LastEditTime: 2020-03-12 19:34:00
 -->
<template>
  <div class="collect">
    <!-- Add a static page for my favorite module -->
    <div class="collect-header">
      <div class="collect-title">
        <i class="el-icon-collection-tag" style="color: #5b6ef5;"></i>
        我的收藏
      </div>
    </div>
    <div class="content">
      <div class="goods-list" v-if="collectList.length>0">
        <MyList :list="collectList" :isDelete="true" @item-deleted="removeItem"></MyList>
      </div>
      <!-- 收藏列表为空的时候显示的内容 -->
      <div v-else class="collect-empty">
        <div class="empty">
          <h2>您的收藏还是空的！</h2>
          <p>快去购物吧！</p>
          <el-button type="primary" round class="empty-cta" @click="$router.push('/goods')">去逛逛</el-button>
        </div>
      </div>
      <!--  收藏列表为空的时候显示的内容END -->
    </div>
  </div>
</template>
<script>
export default {
  data() {
    return {
      collectList: []
    };
  },
  activated() {
    // 获取收藏数据（activated 每次进入页面刷新，keep-alive 下的正确姿势）
    this.$axios
      .get("/api/collect/user")
      .then(res => {
        if (res.data.code) {
          this.collectList = res.data.data;
        }
      })
      .catch(() => {
        this.notifyError("网络异常，收藏加载失败");
      });
  },
  methods: {
    // 子组件删除成功后同步移除列表行（修复历史 bug：删除后商品残留页面）
    removeItem(productId) {
      const idx = this.collectList.findIndex(t => t.productId == productId);
      if (idx > -1) {
        this.collectList.splice(idx, 1);
      }
    }
  }
};
</script>
<style>
.collect {
  background-color: #f5f5f5;
}
.collect .collect-header {
  height: 64px;
  background-color: #fff;
  border-bottom: 2px solid #5b6ef5;
}
.collect .collect-header .collect-title {
  width: 1225px;
  margin: 0 auto;
  height: 64px;
  line-height: 58px;
  font-size: 28px;
}
.collect .content {
  padding: 20px 0;
  width: 1225px;
  margin: 0 auto;
}
.collect .content .goods-list {
  margin-left: -13.7px;
  overflow: hidden;
}
/* 收藏列表为空的时候显示的内容CSS */
.collect .collect-empty {
  width: 1225px;
  margin: 0 auto;
}
.collect .collect-empty .empty {
  height: 300px;
  padding: 0 0 130px 558px;
  margin: 65px 0 0;
  background: url(../assets/imgs/cart-empty.png) no-repeat 124px 0;
  color: #b0b0b0;
  overflow: hidden;
}
.collect .collect-empty .empty h2 {
  margin: 70px 0 15px;
  font-size: 36px;
}
.collect .collect-empty .empty p {
  margin: 0 0 20px;
  font-size: 20px;
}
.collect .collect-empty .empty .empty-cta {
  background: #5b6ef5;
  border-color: #5b6ef5;
}
/* 收藏列表为空的时候显示的内容CSS END */
</style>