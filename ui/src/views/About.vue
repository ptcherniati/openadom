<template>
  <div class="about">
    <h1 id="helloWorld">
      {{ message }}
    </h1>
    <button @click="helloWorld()">
      dis bonjour
    </button>
  </div>
</template>
<script>
import http from "@/http/http.js";
export default {
  data() {
    return {
      message: null
    };
  },
  methods: {
    helloWorld: function() {
      http
        .helloWorld()
        .then((response) => {
          this.message = response.data;
        })
        .catch(error => console.log(error));
    }
  }
};
</script>

