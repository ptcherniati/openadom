import Login from './Login.vue.js';
import Application from './Application.vue.js';

new Vue({
  el: '#app',
  data: {
      message: 'Hello Vue!'
    },
  components: {
    Login, Application
  }
});