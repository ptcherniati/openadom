<template>
  <PageView class="with-submenu">
    <SubMenu
      :aria-label="$t('menu.aria-sub-menu')"
      :paths="subMenuPaths"
      :root="application.localName || application.title"
      role="navigation"
    />

    <h1 class="title main-title">
      <span>{{ $t("dataTypeAuthorizations.title", { label: currentUser.label }) }}</span>
    </h1>
    <caption v-if="!this.columnsVisible" class="columns">
      <div class="column loader-wrapper">
        <div class="loader is-loading"></div>
      </div>
    </caption>
    <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
      <FieldsForm
        :application="application"
        :comment="comment"
        :description="description"
        :fields="fields"
        :format="format"
        :ref-values="references"
        :showComment="true"
        pathForKey="rightsRequest.format"
        @update:fields="updateFields"
        @update:comment="updateComment"
      >
      </FieldsForm>
      <div v-for="(datatypeInfos, datatype) in datatypes" :key="datatype">
        <div v-if="dataGroups[datatype] && authReferences[datatype] && columnsVisible[datatype]">
          <AuthorizationTableForDatatype
            :auth-references="authReferences[datatype]"
            :authorization="authorization.authorizations[datatype]"
            :authorization-scopes="authorizationScopes[datatype]"
            :columns-visible="columnsVisible[datatype]"
            :current-authorization-scope="{}"
            :data-groups="dataGroups[datatype]"
            :datatype="{ id: datatype, name: datatypeInfos.name }"
            :is-root="true"
            :isApplicationAdmin="canManage"
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
                  v-for="(column, indexColumn) of columnsVisible[datatype]"
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
          v-if="isApplicationAdmin"
          icon-left="plus"
          style="margin-bottom: 10px"
          type="is-dark"
          @click="handleSubmit(confirmGrantAuthorization)"
        >
          {{ $t("dataTypeAuthorizations.grantRequests") }}
        </b-button>
        <b-button
          v-else-if="'new' === authorizationId"
          icon-left="plus"
          style="margin-bottom: 10px"
          type="is-dark"
          @click="handleSubmit(createRequest)"
        >
          {{ $t("dataTypeAuthorizations.showRequests") }}
        </b-button>
        <b-button
          v-else
          icon-left="plus"
          style="margin-bottom: 10px"
          type="is-dark"
          @click="handleSubmit(createRequest)"
        >
          {{ $t("dataTypeAuthorizations.modifyRequests") }}
        </b-button>
      </div>
    </ValidationObserver>
  </PageView>
</template>

<script>
import { ValidationObserver } from "vee-validate";

import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AuthorizationService } from "@/services/rest/AuthorizationService";
import { UserPreferencesService } from "@/services/UserPreferencesService";
import { RequestRightsService } from "@/services/rest/RequestRightsService";
import { Component, Prop, Vue, Watch } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { InternationalisationService } from "@/services/InternationalisationService";
import { ApplicationResult } from "@/model/ApplicationResult";
import { ReferenceService } from "@/services/rest/ReferenceService";
import AuthorizationTable from "@/components/common/AuthorizationTable";
import AuthorizationTableForDatatype from "@/components/common/AuthorizationTableForDatatype.vue";
import { Authorization } from "@/model/authorization/Authorization";
import { Authorizations } from "@/model/authorization/Authorizations";
import FieldsForm from "@/components/common/provider/FieldsForm.vue";

@Component({
  components: {
    AuthorizationTable,
    AuthorizationTableForDatatype,
    PageView,
    SubMenu,
    CollapsibleTree,
    ValidationObserver,
    FieldsForm,
    InternationalisationService,
  },
})
export default class DataTypeAuthorizationsRightsRequestView extends Vue {
  @Prop() applicationName;
  @Prop({ default: "new" }) authorizationId;
  __DEFAULT__ = "__DEFAULT__";
  referenceService = ReferenceService.INSTANCE;
  references = {};
  authorizationService = AuthorizationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  userPreferencesService = UserPreferencesService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  requestRightsService = RequestRightsService.INSTANCE;
  authorization = {};
  publicAuthorizations = {};
  ownAuthorizations = [];
  ownAuthorizationsColumnsByPath = {};
  authorizations = [];
  users = [];
  name = null;
  dataGroups = {};
  authorizationScopes = {};
  application = new ApplicationResult();
  selectedUsers = [];
  isApplicationAdmin = false;
  canManage = false;
  isLoading;
  datatypes = [];

  fields = {};
  valid = false;
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
  columnsVisible = false;
  period = this.periods.FROM_DATE_TO_DATE;
  startDate = null;
  endDate = null;
  configuration = {};
  authReferences = {};
  subMenuPaths = [];
  repository = null;
  filteredTags = [];
  format = {};
  description = "";

  currentUser = {};
  comment = null;

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
        this.$t(`dataTypeAuthorizations.sub-menu-request-authorization`),
        () => {
          this.$router.push(`/applications/${this.applicationName}/authorizationsRequest`);
        },
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`)
      ),
      new SubMenuPath(
        this.$t(`dataTypeAuthorizations.sub-menu-new-authorization`),
        () => {},
        () => {
          this.$router.push(`/applications/${this.applicationName}/authorizationsRequest/new`);
        }
      ),
    ];
    this.isLoading = false;
  }

  mounted() {}

  async init() {
    this.isLoading = true;
    try {
      this.application = await this.applicationService.getApplication(this.applicationName, [
        "CONFIGURATION",
        "DATATYPE",
        "RIGHTSREQUEST",
      ]);
      this.datatypes = (Object.keys(this.application.configuration.dataTypes) || []).reduce(
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
      this.format = this.application?.rightsRequest?.description?.format || {};
      this.description =
        this.application?.rightsRequest?.description?.description[
          this.userPreferencesService.getUserPrefLocale()
        ] ||
        this.$t("dataTypeAuthorizations.field_form_description", {
          applicationName: this.application.localName,
        });
      this.fields = (Object.keys(this.format) || []).reduce((acc, field) => {
        acc[field] = "";
        return acc;
      }, {});
      this.configuration = (Object.keys(this.datatypes) || []).reduce((acc, datatype) => {
        acc[datatype] = this.application.configuration.dataTypes[datatype];
        return acc;
      }, {});
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
      };
      this.authorizations = (Object.keys(this.datatypes) || []).reduce((acc, datatype) => {
        acc[datatype] = this.configuration[datatype]?.authorization?.authorizationScopes || [];
        return acc;
      }, {});
      this.repository = (Object.keys(this.datatypes) || []).reduce((acc, datatype) => {
        acc[datatype] = this.application.dataTypes[datatype].repository;
        return acc;
      }, {});
      const grantableInfos = await this.authorizationService.getAuthorizationGrantableInfos(
        this.applicationName
      );
      ({
        authorizationScopes: this.authorizationScopes,
        dataGroups: this.dataGroups,
        users: this.users,
        publicAuthorizations: this.publicAuthorizations,
        isApplicationAdmin: this.isApplicationAdmin,
        ownAuthorizations: this.ownAuthorizations,
        ownAuthorizationsColumnsByPath: this.ownAuthorizationsColumnsByPath,
        columnsVisible: this.columnsVisible,
      } = Authorizations.parseGrantableInfos(grantableInfos, this.datatypes, this.repository));

      if (this.authorizationId !== "new") {
        this.valid = true;
        let request = await this.requestRightsService.getRightsRequests(this.applicationName, {
          uuids: [this.authorizationId],
        });

        this.currentUser = request.users.find(
          (user) =>
            user.id ===
            ((request &&
              request.rightsRequests &&
              request.rightsRequests[0] &&
              request.rightsRequests[0].user) ||
              JSON.parse(localStorage.authenticatedUser).id)
        );
        let rightsRequest = request.rightsRequests[0];
        this.comment = rightsRequest.comment;
        this.fields = (Object.keys(this.format) || []).reduce((acc, field) => {
          acc[field] = rightsRequest.rightsRequestForm[field];
          return acc;
        }, {});
        let authorizations = (rightsRequest && rightsRequest.rightsRequest) || {};
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
        this.authorization = (Object.keys(this.datatypes) || []).reduce((auth, datatype) => {
          auth.authorizations[datatype] = new Authorizations(
            { authorizations: authorizations[datatype] },
            (this.authorizationScopes[datatype] || []).map((as) => as.id)
          );
          return auth;
        }, initialValue);
        this.canManage =
          this.isApplicationAdmin ||
          (authorizations.users &&
            authorizations.users[0].login ===
              JSON.parse(localStorage.getItem("authenticatedUser")).login);
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
        this.authorization = (Object.keys(this.datatypes) || []).reduce((acc, datatype) => {
          acc.authorizations[datatype] = new Authorizations(
            { dataType: datatype, applicationNameOrId: this.applicationName },
            (this.authorizationScopes[datatype] || []).map((as) => as.id)
          );
          return acc;
        }, initialValue);
        this.canManage = true;
      }
      console.log(this.currentUser);
      let currentAuthorizationUsers = this.authorization.users || [];
      this.selectedUsers = (this.users || []).filter((user) => {
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

      let columnsVisible = {};
      for (const datatype in this.columnsVisible) {
        columnsVisible[datatype] = {};
        for (const scope in this.columnsVisible[datatype]) {
          let columnsVisibleFordatatypeAndScope = this.columnsVisible[datatype][scope];
          if (
            columnsVisibleFordatatypeAndScope.forRequest ||
            (columnsVisibleFordatatypeAndScope.display &&
              !columnsVisibleFordatatypeAndScope.forPublic)
          ) {
            columnsVisible[datatype][scope] = columnsVisibleFordatatypeAndScope;
          }
        }
      }
      this.columnsVisible = columnsVisible;
    } catch (error) {
      this.alertService.toastServerError(error);
    }
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

  updateFields(event) {
    this.fields = event.fields;
    this.valid = event.valid;
  }

  updateComment(event) {
    this.comment = event.comment;
    this.valid = event.valid;
  }

  @Watch("period")
  onPeriodChanged() {
    this.endDate = null;
    this.startDate = null;
  }

  async grantAuthorization() {
    try {
      const auth = await this.createAuthorization();
      console.log(auth);
      this.createRequest(true);
    } catch (e) {
      console.log("error", e);
    }
  }

  async createRequest(isSetted) {
    if (!this.valid) {
      return;
    }
    try {
      let authorizationToSend = {
        uuid: this.authorization.uuid,
        name: this.authorization.name,
        applicationNameOrId: this.applicationName,
        authorizations: {},
      };
      authorizationToSend.usersId = this.selectedUsers.map((user) => user.id);
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
      if (!(this.comment && this.comment.length)) {
        this.$buefy.dialog.prompt({
          message: this.$t("dataTypeAuthorizations.addComment"),
          inputAttrs: {
            placeholder: this.$t("dataTypeAuthorizations.commentExample"),
            maxlength: 255,
            minLength: 3,
            canCancel: false,
            confirmText: this.$t("dataTypeAuthorizations.grantRequestConfirm"),
          },
          trapFocus: true,
          onConfirm: (value) => (this.comment = value),
        });
      }
      await this.requestRightsService.createRequestRights(this.applicationName, {
        id: this.authorizationId === "new" ? null : this.authorizationId,
        fields: this.fields,
        rightsRequest: authorizationToSend,
        setted: isSetted,
        comment: this.comment,
      });
      if ("new" === this.authorizationId) {
        this.alertService.toastSuccess(this.$t("alert.create-request"));
      } else if (isSetted) {
        this.alertService.toastSuccess(this.$t("alert.valid-request"));
      } else {
        this.alertService.toastSuccess(this.$t("alert.modified-request"));
      }
      this.$router.push(`/applications/${this.applicationName}/authorizationsRequest`);
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  async createAuthorization() {
    try {
      let authorizationToSend = {
        uuid: this.authorization.uuid,
        name: `request ${this.authorizationId}} for user ${this.currentUser.label}}`,
        applicationNameOrId: this.applicationName,
        authorizations: {},
        usersId: [this.currentUser.id],
      };
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
      const auth = await this.authorizationService.createAuthorization(
        this.applicationName,
        authorizationToSend
      );
      this.alertService.toastSuccess(this.$t("alert.create-authorization"));
      return auth;
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  confirmGrantAuthorization() {
    this.$buefy.dialog.confirm({
      title: this.$t("dataTypeAuthorizations.confirmGrantRequestsTitle"),
      message: this.$t("dataTypeAuthorizations.confirmGrantRequests", this.currentUser),
      cancelText: this.$t("dataTypeAuthorizations.grantRequestDismiss"),
      confirmText: this.$t("dataTypeAuthorizations.grantRequestConfirm"),
      onConfirm: () => this.grantAuthorization(),
    });
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
