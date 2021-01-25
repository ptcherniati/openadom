import Vue from 'vue'

export default Vue.filter('dateFormat', (date) => {
    return date.replace(/-/g, '/');
})