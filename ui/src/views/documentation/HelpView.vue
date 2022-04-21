><template>
  <PageView>
    <h1 class="title main-title">{{ $t("titles.aide") }}</h1>

    <div class="columns columnPrincipale">
      <embed  class="column" src="files/aide.pdf" width=800 height=500 type='application/pdf'/>
    </div>
  </PageView>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";

@Component({
  components: { PageView },
})
export default class HelpView extends Vue {
}
</script>

<style lang="scss" scoped>
// card & modal style
.columns {
  flex-wrap: wrap;
  margin: 0px;
  width: 100%;
  height: 100%;
}
.columns embed {
  width: 100%;
  height: 100%;
}

.column {
  display: grid;

  .comment {
    display: flex;
    align-items: center;
    align-content: start;
  }
  .card {
    &.applicationCard {
      width: 300px;

      .card-footer {
        border: none;
      }
    }

    .btnModal {
      margin: 5px;
      opacity: 50%;
      color: $primary;
      background-color: transparent;
    }

    .card-footer-item {
      border-right: none;
    }
  }
}

.createApplication {
  background-color: $dark;
  color: white;
}

.card-header-title {
  &.title {
    margin-top: 0;
    text-transform: uppercase;
    margin-bottom: 0px;
  }
}

.control.has-icons-left .icon,
.control.has-icons-right .icon {
  top: 5px;
  left: 5px;
}
</style>