<template>
  <PageView class="with-submenu">
    <SubMenu :root="application.title" :paths="subMenuPaths" />

    <h1 class="title main-title">
      <span v-if="authorizationId === 'new'">{{
        $t("titles.data-type-new-authorization", { dataType: dataTypeId })
      }}</span>
    </h1>

    <b-field :label="$t('dataTypeAuthorizations.users')" class="mb-4">
      <b-select
        :placeholder="$t('dataTypeAuthorizations.users-placeholder')"
        multiple
        v-model="usersToAuthorize"
        :native-size="Math.min(users.length, 5)"
        expanded
      >
        <option v-for="user in users" :value="user.id" :key="user.id">
          {{ user.label }}
        </option>
      </b-select>
    </b-field>

    <b-field :label="$t('dataTypeAuthorizations.data-groups')" class="mb-4">
      <b-select
        :placeholder="$t('dataTypeAuthorizations.data-groups-placeholder')"
        v-model="dataGroupToAuthorize"
        :native-size="Math.min(dataGroups.length, 5)"
        expanded
      >
        <option v-for="dataGroup in dataGroups" :value="dataGroup.id" :key="dataGroup.id">
          {{ dataGroup.label }}
        </option>
      </b-select>
    </b-field>

    <b-field :label="$t('dataTypeAuthorizations.authorization-scopes')">
      <b-collapse
        class="card"
        animation="slide"
        v-for="(scope, index) of authorizationScopes"
        :key="scope.id"
        :open="openCollapse == index"
        @open="openCollapse = index"
      >
        <template #trigger="props">
          <div class="card-header" role="button">
            <p class="card-header-title">
              {{ scope.label }}
            </p>
            <a class="card-header-icon">
              <b-icon :icon="props.open ? 'chevron-down' : 'chevron-up'"> </b-icon>
            </a>
          </div>
        </template>
        <div class="card-content">
          <div class="content">
            <CollapsibleTree
              v-for="option in scope.options"
              :key="option.id"
              :option="option"
              :withCheckBoxes="true"
            />
          </div>
        </div>
      </b-collapse>
    </b-field>
  </PageView>
</template>

<script>
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AuthorizationService } from "@/services/rest/AuthorizationService";
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";

@Component({
  components: { PageView, SubMenu, CollapsibleTree },
})
export default class DataTypeAuthorizationInfoView extends Vue {
  @Prop() dataTypeId;
  @Prop() applicationName;
  @Prop() authorizationId;

  authorizationService = AuthorizationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;

  authorizations = [];
  application = {};
  users = [];
  dataGroups = [];
  authorizationScopes = [];
  usersToAuthorize = [];
  dataGroupToAuthorize = {};
  openCollapse = null;

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
      new SubMenuPath(
        this.$t(`dataTypeAuthorizations.sub-menu-new-authorization`),
        () => {},
        () => {
          this.$router.push(
            `/applications/${this.applicationName}/dataTypes/${this.dataTypeId}/authorizations`
          );
        }
      ),
    ];
  }

  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName);
      const grantableInfos = await this.authorizationService.getAuthorizationGrantableInfos(
        this.applicationName,
        this.dataTypeId
      );
      ({
        authorizationScopes: this.authorizationScopes,
        dataGroups: this.dataGroups,
        users: this.users,
      } = grantableInfos);
      console.log(this.authorizationScopes, this.dataGroups, this.users);
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }
}
</script>
