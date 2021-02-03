<template>
  <div>
    <router-view />
  </div>
</template>

<script>
import Configuration from "@/components/Configuration";
import ApplicationChoice from "@/components/application/ApplicationChoice";
import UploadApplication from "@/components/application/UploadApplication";
import EventBus from "@/eventBus";
export default {
  name: "Application",
  mounted() {
  },
  computed: {
    application: {
      get() {
        return this.$store.state.application;
      }
    },
    user: {
      get() {
        return this.$store.state.user;
      }
    },
  },
  data() {
    return {
    };
  },
  methods: {
  },
  components: {
//    ApplicationChoice,
//    UploadApplication,
  }
};
</script>

<style scoped lang="scss">
</style>
