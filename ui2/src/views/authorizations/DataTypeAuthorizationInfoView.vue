<template>
  <PageView class="with-submenu">
    <SubMenu :root="application.title" :paths="subMenuPaths" />

    <h1 class="title main-title">
      <span v-if="authorizationId === 'new'">{{
        $t("titles.data-type-new-authorization", { dataType: dataTypeId })
      }}</span>
    </h1>

    <b-field
      :label="$t('dataTypeAuthorizations.period')"
      class="DataTypeAuthorizationInfoView-periods-container mb-4"
    >
      <b-radio
        name="dataTypeAuthorization-period"
        v-model="period"
        :native-value="periods.FROM_DATE"
        class="DataTypeAuthorizationInfoView-radio-field"
      >
        {{ periods.FROM_DATE }}
        <b-field :label="$t('dataTypeAuthorizations.start-date')" class="mb-4">
          <b-datepicker show-week-number :locale="chosenLocale" icon="calendar-day" trap-focus>
          </b-datepicker>
        </b-field>
      </b-radio>

      <b-radio
        name="dataTypeAuthorization-period"
        v-model="period"
        :native-value="periods.TO_DATE"
        class="DataTypeAuthorizationInfoView-radio-field"
      >
        {{ periods.TO_DATE }}
        <b-field :label="$t('dataTypeAuthorizations.end-date')" class="mb-4">
          <b-datepicker show-week-number :locale="chosenLocale" icon="calendar-day" trap-focus>
          </b-datepicker>
        </b-field>
      </b-radio>

      <b-radio
        name="dataTypeAuthorization-period"
        v-model="period"
        :native-value="periods.FROM_DATE_TO_DATE"
        class="DataTypeAuthorizationInfoView-radio-field"
      >
        {{ periods.FROM_DATE_TO_DATE }}
        <b-field :label="$t('dataTypeAuthorizations.start-date')" class="mb-4">
          <b-datepicker show-week-number :locale="chosenLocale" icon="calendar-day" trap-focus>
          </b-datepicker>
        </b-field>
        <b-field :label="$t('dataTypeAuthorizations.end-date')" class="mb-4">
          <b-datepicker show-week-number :locale="chosenLocale" icon="calendar-day" trap-focus>
          </b-datepicker>
        </b-field>
      </b-radio>

      <b-radio
        name="dataTypeAuthorization-period"
        v-model="period"
        :native-value="periods.ALWAYS"
        >{{ periods.ALWAYS }}</b-radio
      >
    </b-field>

    <b-field :label="$t('dataTypeAuthorizations.users')" class="mb-4">
      <b-select
        :placeholder="$t('dataTypeAuthorizations.users-placeholder')"
        v-model="userToAuthorize"
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
              :withRadios="true"
              :radioName="`dataTypeAuthorizations_${applicationName}_${dataTypeId}`"
              @optionChecked="(value) => (scopeToAuthorize = value)"
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
import { UserPreferencesService } from "@/services/UserPreferencesService";
import { Component, Prop, Vue, Watch } from "vue-property-decorator";
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
  userPreferencesService = UserPreferencesService.INSTANCE;

  periods = {
    FROM_DATE: this.$t("dataTypeAuthorizations.from-date"),
    TO_DATE: this.$t("dataTypeAuthorizations.to-date"),
    FROM_DATE_TO_DATE: this.$t("dataTypeAuthorizations.from-date-to-date"),
    ALWAYS: this.$t("dataTypeAuthorizations.always"),
  };

  authorizations = [];
  application = {};
  users = [];
  dataGroups = [];
  authorizationScopes = [];
  userToAuthorize = [];
  dataGroupToAuthorize = {};
  openCollapse = null;
  scopeToAuthorize = null;
  period = this.periods.FROM_DATE;

  created() {
    this.init();
    this.chosenLocale = this.userPreferencesService.getUserPrefLocale();
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
      // this.authorizationScopes[0].options[0].children[0].children.push({
      //   children: [],
      //   id: "toto",
      //   label: "toto",
      // });
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  @Watch("scopeToAuthorize")
  onScopeToAuthorizeChanged() {
    console.log(this.scopeToAuthorize);
  }
}
</script>

<style lang="scss">
.DataTypeAuthorizationInfoView-periods-container {
  .field-body .field.has-addons {
    display: flex;
    flex-direction: column;
  }
}

.DataTypeAuthorizationInfoView-radio-field {
  &.b-radio {
    .control-label {
      display: flex;
      align-items: center;
    }
  }
}
</style>
