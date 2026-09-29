/*  @Description: 用户登录状态模块 */
export default {
  state: {
    user: "", // 登录的用户
    showLogin: false, // 用于控制是否显示登录组件
    unreadMessage: 0 // 消息中心未读数
  },
  getters: {
    getUser (state) {
      return state.user
    },
    getShowLogin (state) {
      return state.showLogin
    },
    getUnreadMessage (state) {
      return state.unreadMessage
    }
  },
  mutations: {
    setUser (state, data) {
      state.user = data;
    },
    setShowLogin (state, data) {
      state.showLogin = data;
    },
    setUnreadMessage (state, data) {
      state.unreadMessage = data;
    }
  },
  actions: {
    setUser ({ commit }, data) {
      commit('setUser', data);
    },
    setShowLogin ({ commit }, data) {
      commit('setShowLogin', data);
    },
    setUnreadMessage ({ commit }, data) {
      commit('setUnreadMessage', data);
    }
  }
}
