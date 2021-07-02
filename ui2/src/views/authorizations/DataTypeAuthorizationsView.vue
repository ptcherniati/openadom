<template>
  <PageView class="with-submenu">
    <SubMenu :root="application.title" :paths="subMenuPaths" />
    <h1 class="title main-title">
      {{ $t("titles.data-type-authorizations", { dataType: dataTypeId }) }}
    </h1>
    <div class="buttons">
      <b-button type="is-primary" @click="addAuthorization" icon-left="plus">
        {{ $t("dataTypeAuthorizations.add-auhtorization") }}
      </b-button>
    </div>

    <b-table
      :data="authorizations"
      :striped="true"
      :isFocusable="true"
      :isHoverable="true"
      :sticky-header="true"
      :paginated="true"
      :per-page="15"
      height="100%"
    >
      <b-table-column
        b-table-column
        field="user"
        :label="$t('dataTypeAuthorizations.user')"
        sortable
        v-slot="props"
      >
        {{ props.row.id }}
      </b-table-column>
      <b-table-column
        b-table-column
        field="dataType"
        :label="$t('dataTypeAuthorizations.data-type')"
        sortable
        v-slot="props"
      >
        {{ props.row.dataType }}
      </b-table-column>
      <b-table-column
        b-table-column
        field="dataGroup"
        :label="$t('dataTypeAuthorizations.data-group')"
        sortable
        v-slot="props"
      >
        {{ props.row.dataGroup }}
      </b-table-column>
      <b-table-column
        v-for="scope in scopes"
        :key="scope"
        b-table-column
        :label="scope"
        sortable
        v-slot="props"
      >
        {{ props.row.authorizedScopes[scope] }}
      </b-table-column>
    </b-table>
  </PageView>
</template>

<script>
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AuthorizationService } from "@/services/rest/AuthorizationService";
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";

@Component({
  components: { PageView, SubMenu },
})
export default class DataTypeAuthorizationsView extends Vue {
  @Prop() dataTypeId;
  @Prop() applicationName;

  authorizationService = AuthorizationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;

  authorizations = [];
  application = {};
  scopes = [];

  created() {
    this.init();
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("dataTypesManagement.data-types").toLowerCase(),
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`),
        () => this.$router.push("/applications")
      ),
      new SubMenuPath(
        this.$t(`dataTypeAuthorizations.sub-menu-data-type-authorizations`, {
          dataType: this.dataTypeId,
        }),
        () => {
          this.$router.push(
            `/applications/${this.applicationName}/dataTypes/${this.dataTypeId}/authorizations`
          );
        },
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`)
      ),
    ];
  }

  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName);
      this.authorizations = await this.authorizationService.getDataAuthorizations(
        this.applicationName,
        this.dataTypeId
      );
      if (this.authorizations && this.authorizations.length !== 0) {
        this.scopes = Object.keys(this.authorizations[0].authorizedScopes);
      }
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  addAuthorization() {
    this.$router.push(
      `/applications/${this.applicationName}/dataTypes/${this.dataTypeId}/authorizations/new`
    );
  }
}
</script>
