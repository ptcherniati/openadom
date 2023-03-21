<template>
  <PageView class="with-submenu">
    <SubMenu
      :aria-label="$t('menu.aria-sub-menu')"
      :paths="subMenuPaths"
      :root="application.localName || application.title"
      role="navigation"
    />

    <h1 class="title main-title">
      <span v-if="authorizationId === 'new'">{{ $t("titles.data-type-new-authorization") }}</span>
    </h1>
    <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
      <div class="columns">
        <b-switch v-model="isPublicAuthorizations" type="is-dark">
          {{ $t("dataTypeAuthorizations.publicAuthorization") }}
        </b-switch>
        <b-field
          v-if="!isApplicationAdmin && !authorizationAdminUser"
          class="column"
          :label="$t('dataTypeAuthorizations.users')"
        >
          <b-tag
            v-for="userSelect in selectedUsers"
            :key="userSelect"
            size="is-medium"
            v-model="selectedUsers"
            field="label"
            type="is-dark"
          >
            {{ userSelect.label }}
          </b-tag>
        </b-field>
        <ValidationProvider
          v-if="!isPublicAuthorizations && (isApplicationAdmin || authorizationAdminUser)"
          v-slot="{ errors, valid }"
          class="column is-half"
          name="users"
          rules="required"
          vid="users"
        >
          <b-field
            :label="$t('dataTypeAuthorizations.users')"
            :message="errors[0]"
            :type="{
              'is-danger': errors && errors.length > 0,
              'is-success': valid,
            }"
            class="column mb-4"
          >
            <b-taginput
              v-model="selectedUsers"
              :data="users.filter((user) => !selectedUsers.includes(user))"
              :open-on-focus="openOnFocus"
              :placeholder="$t('dataTypeAuthorizations.users-placeholder')"
              autocomplete
              expanded
              field="label"
              type="is-dark"
              @typing="getFilteredTags"
            >
            </b-taginput>
            <b-tooltip :label="$t('dataTypeAuthorizations.closeUser')" position="is-bottom">
              <b-button v-model="openOnFocus" icon-left="times-circle"> </b-button>
            </b-tooltip>
          </b-field>
        </ValidationProvider>
        <ValidationProvider
          v-slot="{ errors, valid }"
          class="column is-5"
          name="users"
          rules="required"
          vid="users"
        >
          <b-field
            :label="$t('dataTypeAuthorizations.name')"
            :message="errors[0]"
            :type="{
              'is-danger': errors && errors.length > 0,
              'is-success': valid,
            }"
            class="column mb-4"
          >
            <b-input v-model="authorization.name" />
          </b-field>
        </ValidationProvider>
      </div>
      <div v-for="(datatypeInfos, datatype) in datatypes" :key="datatype">
        <div
          v-if="
            isAuthorized(datatype) &&
            dataGroups[datatype] &&
            authReferences[datatype] &&
            columnsVisible[datatype]
          "
        >
          <AuthorizationTableForDatatype
            :auth-references="authReferences[datatype]"
            :authorization="authorization.authorizations[datatype]"
            :authorization-scopes="authorizationScopes[datatype]"
            :columns-visible="
              isPublicAuthorizations ? columnsVisibleForPublic[datatype] : columnsVisible[datatype]
            "
            :current-authorization-scope="{}"
            :data-groups="dataGroups[datatype]"
            :datatype="{ id: datatype, name: datatypeInfos.name }"
            :is-root="true"
            :isApplicationAdmin="isApplicationAdmin"
            :ownAuthorizations="ownAuthorizations[datatype]"
            :ownAuthorizationsColumnsByPath="ownAuthorizationsColumnsByPath[datatype]"
            :publicAuthorizations="publicAuthorizations[datatype] || {}"
            class="rows"
            @modifyAuthorization="modifyAuthorization($event, datatype)"
            @registerCurrentAuthorization="registerCurrentAuthorization($event, datatype)"
          >
            <div class="row">
              <div class="columns">
                <b-field
                  v-for="(column, indexColumn) of isPublicAuthorizations
                    ? columnsVisibleForPublic[datatype]
                    : columnsVisible[datatype]"
                  :key="indexColumn"
                  :field="indexColumn"
                  :label="getColumnTitle(column)"
                  :style="!column.display ? 'display : contents' : ''"
                  class="column"
                ></b-field>
              </div>
            </div>
          </AuthorizationTableForDatatype>
        </div>
      </div>

      <div class="buttons">
        <b-button
          icon-left="plus"
          style="margin-bottom: 10px"
          type="is-dark"
          @click="handleSubmit(createAuthorization)"
        >
          {{
            authorization.uuid
              ? $t("dataTypeAuthorizations.modify")
              : $t("dataTypeAuthorizations.create")
          }}
        </b-button>
      </div>
    </ValidationObserver>
  </PageView>
</template>

<script>
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AuthorizationService } from "@/services/rest/AuthorizationService";
import { UserPreferencesService } from "@/services/UserPreferencesService";
import { ValidationObserver, ValidationProvider } from "vee-validate";
import { Component, Prop, Vue, Watch } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { InternationalisationService } from "@/services/InternationalisationService";
import { ApplicationResult } from "@/model/ApplicationResult";
import { ReferenceService } from "@/services/rest/ReferenceService";
import AuthorizationTable from "@/components/common/AuthorizationTable";
import AuthorizationTableForDatatype from "@/components/common/AuthorizationTableForDatatype.vue";
import { Authorization } from "@/model/authorization/Authorization";
import { Authorizations } from "@/model/authorization/Authorizations";

@Component({
  components: {
    AuthorizationTable,
    AuthorizationTableForDatatype,
    PageView,
    SubMenu,
    CollapsibleTree,
    ValidationObserver,
    ValidationProvider,
  },
})
export default class DataTypeAuthorizationInfoView extends Vue {
  @Prop() applicationName;
  @Prop({ default: "new" }) authorizationId;
  __DEFAULT__ = "__DEFAULT__";
  referenceService = ReferenceService.INSTANCE;
  references = {};
  authorizationService = AuthorizationService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  userPreferencesService = UserPreferencesService.INSTANCE;
  authorization = {};
  publicAuthorizations = {};
  ownAuthorizations = [];
  ownAuthorizationsColumnsByPath = {};
  authorizations = [];
  users = [];
  publicUsers = [];
  name = null;
  dataGroups = {};
  authorizationScopes = {};
  application = new ApplicationResult();
  selectedUsers = [];
  isApplicationAdmin = false;
  isLoading;
  datatypes = [];
  openOnFocus = true;
  periods = {
    FROM_DATE: this.$t("dataTypeAuthorizations.from-date"),
    TO_DATE: this.$t("dataTypeAuthorizations.to-date"),
    FROM_DATE_TO_DATE: this.$t("dataTypeAuthorizations.from-date-to-date"),
    ALWAYS: this.$t("dataTypeAuthorizations.always"),
  };
  COLUMNS_VISIBLE = {
    label: {
      title: "Label",
      display: true,
      internationalizationName: { fr: "Domaine", en: "Domain" },
    },
  };
  columnsVisible = {};
  columnsVisibleForPublic = {};
  period = this.periods.FROM_DATE_TO_DATE;
  startDate = null;
  endDate = null;
  configuration = {};
  authReferences = {};
  subMenuPaths = [];
  repository = null;
  filteredTags = [];
  isPublicAuthorizations = false;
  authorizationAdminUser = false;

  @Watch("authReferences")
  onExternalOpenStateChanged(newVal) {
    this.authReferences = newVal;
  }

  getColumnTitle(column) {
    if (column.display) {
      return (
        (column.internationalizationName && column.internationalizationName[this.$i18n.locale]) ||
        column.title
      );
    }
  }

  modifyAuthorization(event) {
    let datatype = event.datatype;
    var authorization = this.authorization.authorizations[datatype];
    var authorizations = authorization.authorizations[event.indexColumn] || [];
    for (const authorizationKeytoAdd in event.authorizations.toAdd) {
      authorizations.push(event.authorizations.toAdd[authorizationKeytoAdd]);
    }
    for (const authorizationKeytoDelete in event.authorizations.toDelete) {
      var toDeleteElement = event.authorizations.toDelete[authorizationKeytoDelete];
      authorizations = authorizations.filter((auth) => {
        return !new Authorization(auth).equals(
          toDeleteElement,
          this.authorizationScopes[datatype].map((scope) => scope.id)
        );
      });
    }
    authorization.authorizations[event.indexColumn] = authorizations;
    this.$set(
      this.authorization.authorizations,
      datatype,
      new Authorizations(
        authorization,
        this.authorizationScopes[datatype].map((as) => as.id)
      )
    );
  }

  registerCurrentAuthorization(event) {
    let datatype = event.datatype;
    var authorization = this.authorization.authorizations[event.datatype];
    var authorizations = authorization.authorizations[event.indexColumn] || [];
    var authorizationToReplace = event.authorizations;
    authorizationToReplace.fromDay = authorizationToReplace.from && [
      authorizationToReplace.from.getFullYear(),
      authorizationToReplace.from.getMonth() + 1,
      authorizationToReplace.from.getDate(),
    ];
    authorizationToReplace.toDay = authorizationToReplace.to && [
      authorizationToReplace.to.getFullYear(),
      authorizationToReplace.to.getMonth() + 1,
      authorizationToReplace.to.getDate(),
    ];
    authorizations = authorizations.map((auth) => {
      if (
        !new Authorization(auth).equals(
          authorizationToReplace,
          this.authorizationScopes[datatype].map((scope) => scope.id)
        )
      ) {
        return auth;
      } else {
        return authorizationToReplace;
      }
    });
    authorization.authorizations[event.indexColumn] = authorizations;
    this.$set(
      this.authorization.authorizations,
      event.datatype,
      new Authorizations(
        authorization,
        this.authorizationScopes.map((as) => as.id)
      )
    );
  }

  async created() {
    this.init();
    this.chosenLocale = this.userPreferencesService.getUserPrefLocale();
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("dataTypesManagement.data-types").toLowerCase(),
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`),
        () => this.$router.push("/applications")
      ),
      new SubMenuPath(
        this.$t(`dataTypeAuthorizations.sub-menu-data-type-authorizations`),
        () => {
          this.$router.push(`/applications/${this.applicationName}/authorizations`);
        },
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`)
      ),
      new SubMenuPath(
        this.$t(`dataTypeAuthorizations.sub-menu-new-authorization`),
        () => {},
        () => {
          this.$router.push(`/applications/${this.applicationName}/authorizations`);
        }
      ),
    ];
    this.isLoading = false;
  }

  mounted() {}

  showDetail(parent) {
    for (const child in parent) {
      if (parent[child].children.length !== 0) {
        parent[child] = { ...parent[child], showDetailIcon: true };
      }
      parent[child] = { ...parent[child], showDetailIcon: false };
    }
  }

  async init() {
    this.isLoading = true;
    try {
      this.application = await this.applicationService.getApplication(this.applicationName, [
        "CONFIGURATION",
        "DATATYPE",
      ]);
      this.datatypes = Object.keys(this.application.configuration.dataTypes).reduce(
        (acc, datatype) => {
          acc[datatype] = {
            name:
              this.internationalisationService.localeDataTypeIdName(
                this.application,
                this.application.dataTypes[datatype]
              ) || datatype,
          };
          return acc;
        },
        {}
      );
      this.configuration = Object.keys(this.datatypes).reduce((acc, datatype) => {
        acc[datatype] = this.application.configuration.dataTypes[datatype];
        return acc;
      }, {});
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
      };
      this.authorizations = Object.keys(this.datatypes).reduce((acc, datatype) => {
        acc[datatype] = this.configuration[datatype]?.authorization?.authorizationScopes || [];
        return acc;
      }, {});
      this.repository = Object.keys(this.datatypes).reduce((acc, datatype) => {
        acc[datatype] = this.application.dataTypes[datatype].repository;
        return acc;
      }, {});
      const grantableInfos = await this.authorizationService.getAuthorizationGrantableInfos(
        this.applicationName
      );
      ({
        authorizationScopes: this.authorizationScopes,
        dataGroups: this.dataGroups,
        publicUser: this.publicUser,
        users: this.users,
        publicAuthorizations: this.publicAuthorizations,
        isApplicationAdmin: this.isApplicationAdmin,
        ownAuthorizations: this.ownAuthorizations,
        ownAuthorizationsColumnsByPath: this.ownAuthorizationsColumnsByPath,
        columnsVisible: this.columnsVisible,
        columnsVisibleForPublic: this.columnsVisibleForPublic,
      } = Authorizations.parseGrantableInfos(grantableInfos, this.datatypes, this.repository));

      if (this.authorizationId !== "new") {
        //TODO
        var authorizations = await this.authorizationService.getAuthorizations(
          this.applicationName,
          this.authorizationId
        );
        let initialValue = new Authorizations(
          {
            authorizations: {},
            applicationNameOrId: this.applicationName,
            users: authorizations.users,
            name: authorizations.name,
            uuid: authorizations.uuid,
          },
          []
        );
        this.authorization = Object.keys(this.datatypes).reduce((auth, datatype) => {
          auth.authorizations[datatype] = new Authorizations(
            { authorizations: authorizations.authorizations[datatype] },
            (this.authorizationScopes[datatype] || []).map((as) => as.id)
          );
          return auth;
        }, initialValue);
        this.isPublicAuthorizations =
          authorizations.users[0] && authorizations.users[0].login === "_public_";
      } else {
        let initialValue = new Authorizations(
          {
            authorizations: {},
            applicationNameOrId: this.applicationName,
            users: [],
            name: "",
            uuid: null,
          },
          []
        );
        this.authorization = Object.keys(this.datatypes).reduce((acc, datatype) => {
          acc.authorizations[datatype] = new Authorizations(
            { dataType: datatype, applicationNameOrId: this.applicationName },
            (this.authorizationScopes[datatype] || []).map((as) => as.id)
          );
          return acc;
        }, initialValue);
      }
      let currentAuthorizationUsers = this.authorization.users || [];
      this.selectedUsers = this.users.filter((user) => {
        return currentAuthorizationUsers.find((u) => {
          return u.id === user.id;
        });
      });
      this.selectedUsers.sort();
      this.authReferences = await Authorizations.initAuthReferences(
        this.configuration,
        this.authorizations,
        this.authorizationScopes,
        this.getOrLoadReferences
      );
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  getFilteredTags(text) {
    this.filteredTags = this.users.filter((option) => {
      return option.label.toString().toLowerCase().indexOf(text.toLowerCase()) >= 0;
    });
  }

  async getOrLoadReferences(reference) {
    if (this.references[reference]) {
      return this.references[reference];
    }
    let ref = await this.referenceService.getReferenceValues(this.applicationName, reference);
    this.references[reference] = ref;
    // eslint-disable-next-line no-self-assign
    this.references = this.references;
    return ref;
  }

  @Watch("period")
  onPeriodChanged() {
    this.endDate = null;
    this.startDate = null;
  }

  async createAuthorization() {
    try {
      let authorizationToSend = {
        uuid: this.authorization.uuid,
        name: this.authorization.name,
        applicationNameOrId: this.applicationName,
        authorizations: {},
      };
      authorizationToSend.usersId = (
        this.isPublicAuthorizations ? this.publicUsers : this.selectedUsers
      ).map((user) => user.id);
      for (const datatype in this.authorization.authorizations) {
        let authorizationForDatatype = this.authorization.authorizations[datatype].authorizations;
        for (const scope in authorizationForDatatype) {
          authorizationForDatatype[scope] = authorizationForDatatype[scope].map((auth) => {
            var returnedAuth = new Authorization(auth);
            returnedAuth.intervalDates = {
              fromDay: returnedAuth.fromDay,
              toDay: returnedAuth.toDay,
            };
            returnedAuth.dataGroups = returnedAuth.dataGroups.map((dg) => dg.id || dg);
            return returnedAuth;
          });
          authorizationToSend.authorizations[datatype] = authorizationForDatatype;
        }
      }
      await this.authorizationService.createAuthorization(
        this.applicationName,
        authorizationToSend
      );
      this.alertService.toastSuccess(this.$t("alert.create-authorization"));
      this.$router.push(`/applications/${this.applicationName}/authorizations`);
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  isAuthorized(datatype) {
    const ownAuthorizationsColumnsByPathElementForDatatype =
      this.ownAuthorizationsColumnsByPath[datatype];
    if (
      Object.values(this.ownAuthorizationsColumnsByPath[datatype] || []).some((scopes) =>
        scopes.includes("admin")
      )
    )
      this.authorizationAdminUser = true;
    return (
      this.isApplicationAdmin ||
      Object.values(ownAuthorizationsColumnsByPathElementForDatatype || []).some((scopes) =>
        scopes.includes("admin")
      )
    );
  }

  extractAuthorizations(authorizationTree) {
    var authorizationArray = [];
    if (!authorizationTree || Object.keys(authorizationTree || []).length === 0) {
      return authorizationArray;
    }
    for (const key in authorizationTree) {
      var treeOrAuthorization = authorizationTree[key];
      authorizationArray = [
        ...authorizationArray,
        ...(treeOrAuthorization instanceof Authorization
          ? [treeOrAuthorization.parse()]
          : this.extractAuthorizations(treeOrAuthorization)),
      ];
    }
    return authorizationArray;
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

.leaf label {
  font-weight: lighter;
  font-style: italic;
  color: #2c3e50;
}

.folder label {
  font-weight: bolder;
  color: $dark;
}

.rows .card-content .row.label .columns .column {
  padding: 0 0 0 10px;
  border-bottom: 2px solid;
  border-color: $dark;
  margin-bottom: 12px;
}

ul li.card-content {
  background-color: rgba(0, 0, 0, 0.05);
}

a {
  color: $dark;
}
</style>
