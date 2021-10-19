<template>
  <PageView class="with-submenu">
    <SubMenu :root="application.localName || application.title" :paths="subMenuPaths" />

    <h1 class="title main-title">
      <span v-if="authorizationId === 'new'">{{
        $t("titles.data-type-new-authorization", {
          dataType: application.dataTypeId || dataTypeId,
        })
      }}</span>
    </h1>

    <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
      <b-field
        :label="$t('dataTypeAuthorizations.period')"
        class="DataTypeAuthorizationInfoView-periods-container mb-4"
      >
        <b-radio
          class="DataTypeAuthorizationInfoView-radio-field"
          name="dataTypeAuthorization-period"
          v-model="period"
          :native-value="periods.ALWAYS"
        >
          <span class="DataTypeAuthorizationInfoView-radio-label"> {{ periods.ALWAYS }}</span>
        </b-radio>
        <b-radio
          name="dataTypeAuthorization-period"
          v-model="period"
          :native-value="periods.FROM_DATE_TO_DATE"
          class="DataTypeAuthorizationInfoView-radio-field mb-2"
        >
          <span class="DataTypeAuthorizationInfoView-radio-label">
            {{ periods.FROM_DATE_TO_DATE }}
          </span>
          <ValidationProvider
            :rules="period === periods.FROM_DATE_TO_DATE ? 'required' : ''"
            name="period_fromDateToDate_1"
            v-slot="{ errors, valid }"
            vid="period_fromDateToDate_1"
          >
            <b-field
              class="mr-4"
              :type="{
                'is-danger': errors && errors.length > 0,
                'is-success': valid && period === periods.FROM_DATE_TO_DATE,
              }"
              :message="errors[0]"
            >
              <b-datepicker
                v-model="startDate"
                show-week-number
                :locale="chosenLocale"
                icon="calendar-day"
                trap-focus
                :disabled="period !== periods.FROM_DATE_TO_DATE"
              >
              </b-datepicker>
            </b-field>
          </ValidationProvider>
          <span class="mr-4">{{ $t("dataTypeAuthorizations.to") }}</span>
          <ValidationProvider
            :rules="period === periods.FROM_DATE_TO_DATE ? 'required' : ''"
            name="period_fromDateToDate_2"
            v-slot="{ errors, valid }"
            vid="period_fromDateToDate_2"
          >
            <b-field
              :type="{
                'is-danger': errors && errors.length > 0,
                'is-success': valid && period === periods.FROM_DATE_TO_DATE,
              }"
              :message="errors[0]"
            >
              <b-datepicker
                v-model="endDate"
                show-week-number
                :locale="chosenLocale"
                icon="calendar-day"
                trap-focus
                :disabled="period !== periods.FROM_DATE_TO_DATE"
              >
              </b-datepicker>
            </b-field>
          </ValidationProvider>
        </b-radio>
      </b-field>

      <ValidationProvider rules="required" name="users" v-slot="{ errors, valid }" vid="users">
        <b-field
          :label="$t('dataTypeAuthorizations.users')"
          class="mb-4"
          :type="{
            'is-danger': errors && errors.length > 0,
            'is-success': valid,
          }"
          :message="errors[0]"
        >
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
      </ValidationProvider>

      <ValidationProvider
        rules="required"
        name="dataGroups"
        v-slot="{ errors, valid }"
        vid="dataGroups"
      >
        <b-field
          :label="$t('dataTypeAuthorizations.data-groups')"
          :type="{
            'is-danger': errors && errors.length > 0,
            'is-success': valid,
          }"
          :message="errors[0]"
        >
          <b-taginput
            type="is-primary"
            v-model="dataGroupToAuthorize"
            :data="dataGroups"
            :value="dataGroups.id"
            autocomplete
            :open-on-focus="true"
            field="label"
            :placeholder="$t('dataTypeAuthorizations.data-groups-placeholder')"
          >
          </b-taginput>
        </b-field>
      </ValidationProvider>

      <ValidationProvider rules="required" name="scopes" v-slot="{ errors, valid }" vid="scopes">
        <b-field
          :label="$t('dataTypeAuthorizations.authorization-scopes')"
          class="mb-4"
          :type="{
            'is-danger': errors && errors.length > 0,
            'is-success': valid,
          }"
          :message="errors[0]"
        >
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
                  @optionChecked="(value) => (scopesToAuthorize[scope.id] = value)"
                />
              </div>
            </div>
          </b-collapse>
        </b-field>
      </ValidationProvider>

      <div class="buttons">
        <b-button type="is-primary" @click="handleSubmit(createAuthorization)" icon-left="plus">
          {{ $t("dataTypeAuthorizations.create") }}
        </b-button>
      </div>
    </ValidationObserver>
  </PageView>
</template>

<script>
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { DataTypeAuthorization } from "@/model/DataTypeAuthorization";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AuthorizationService } from "@/services/rest/AuthorizationService";
import { UserPreferencesService } from "@/services/UserPreferencesService";
import { ValidationObserver, ValidationProvider } from "vee-validate";
import { Component, Prop, Vue, Watch } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import { InternationalisationService } from "@/services/InternationalisationService";

@Component({
  components: { PageView, SubMenu, CollapsibleTree, ValidationObserver, ValidationProvider },
})
export default class DataTypeAuthorizationInfoView extends Vue {
  @Prop() dataTypeId;
  @Prop() applicationName;
  @Prop() authorizationId;

  authorizationService = AuthorizationService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
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
  application = new ApplicationResult();
  users = [];
  dataGroups = [];
  authorizationScopes = [];
  userToAuthorize = null;
  dataGroupToAuthorize = null;
  openCollapse = null;
  scopesToAuthorize = {};
  period = this.periods.FROM_DATE_TO_DATE;
  startDate = null;
  endDate = null;

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
      this.application = {
        ...this.application,
        localName: this.internationalisationService.localeApplicationName(this.application),
        dataTypeId: this.internationalisationService.localeDatatypeNameApplication(
          this.application,
          this.dataTypeId
        ),
      };
      const grantableInfos = await this.authorizationService.getAuthorizationGrantableInfos(
        this.applicationName,
        this.dataTypeId
      );
      ({
        authorizationScopes: this.authorizationScopes,
        dataGroups: this.dataGroups,
        users: this.users,
      } = grantableInfos);
      // this.authorizationScopes[0].options[0].children[0].children.push({
      //   children: [],
      //   id: "toto",
      //   label: "toto",
      // });
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  @Watch("period")
  onPeriodChanged() {
    this.endDate = null;
    this.startDate = null;
  }

  async createAuthorization() {
    const dataTypeAuthorization = new DataTypeAuthorization();
    dataTypeAuthorization.userId = this.userToAuthorize;
    dataTypeAuthorization.applicationNameOrId = this.applicationName;
    dataTypeAuthorization.dataType = this.dataTypeId;
    dataTypeAuthorization.dataGroup = this.dataGroupToAuthorize;
    dataTypeAuthorization.authorizedScopes = this.scopesToAuthorize;
    let fromDay = null;
    if (this.startDate) {
      fromDay = [
        this.startDate.getFullYear(),
        this.startDate.getMonth() + 1,
        this.startDate.getDate(),
      ];
    }
    dataTypeAuthorization.fromDay = fromDay;
    let toDay = null;
    if (this.endDate) {
      toDay = [this.endDate.getFullYear(), this.endDate.getMonth() + 1, this.endDate.getDate()];
    }
    dataTypeAuthorization.toDay = toDay;

    try {
      await this.authorizationService.createAuthorization(
        this.applicationName,
        this.dataTypeId,
        dataTypeAuthorization
      );
      this.alertService.toastSuccess(this.$t("alert.create-authorization"));
      this.$router.push(
        `/applications/${this.applicationName}/dataTypes/${this.dataTypeId}/authorizations`
      );
    } catch (error) {
      this.alertService.toastServerError(error);
    }
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
  height: 40px;

  &.b-radio {
    .control-label {
      display: flex;
      align-items: center;
      width: 100%;
    }
  }
}

.DataTypeAuthorizationInfoView-radio-label {
  width: 200px;
}

.collapse-content .card-content .content .CollapsibleTree-header .CollapsibleTree-buttons {
  visibility: hidden;
  display: none;
}
</style>
