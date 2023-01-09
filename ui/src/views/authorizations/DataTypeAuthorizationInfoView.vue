<template>
  <PageView class="with-submenu">
    <SubMenu
      :paths="subMenuPaths"
      :root="application.localName || application.title"
      role="navigation"
      :aria-label="$t('menu.aria-sub-menu')"
    />

    <h1 class="title main-title">
      <span v-if="authorizationId === 'new'">{{
        $t("titles.data-type-new-authorization", {
          dataType: application.localDatatypeName || dataTypeId,
        })
      }}</span>
    </h1>
    <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
      <div class="columns">
        <ValidationProvider
          v-slot="{ errors, valid }"
          name="users"
          rules="required"
          vid="users"
          class="column is-half"
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
              expanded
              v-model="selectedlabels"
              :data="userLabels"
              :value="userLabels"
              autocomplete
              type="is-dark"
              :placeholder="$t('dataTypeAuthorizations.users-placeholder')"
              @typing="getFilteredTags"
            >
            </b-taginput>
          </b-field>
        </ValidationProvider>
        <ValidationProvider
          v-slot="{ errors, valid }"
          name="users"
          rules="required"
          vid="users"
          class="column is-half"
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
      <AuthorizationTable
        v-if="dataGroups && authReferences && columnsVisible && authReferences[0]"
        :auth-reference="authReferences[0]"
        :authorization-scopes="authorizationScopes"
        :columns-visible="columnsVisible"
        :data-groups="dataGroups"
        :remaining-option="authReferences.slice && authReferences.slice(1, authReferences.length)"
        :authorization="authorization"
        :isApplicationAdmin="isApplicationAdmin"
        :publicAuthorizations="publicAuthorizations"
        :ownAuthorizations="ownAuthorizations"
        :ownAuthorizationsColumnsByPath="ownAuthorizationsColumnsByPath"
        :current-authorization-scope="{}"
        :is-root="true"
        class="rows"
        @modifyAuthorization="modifyAuthorization($event)"
        @registerCurrentAuthorization="registerCurrentAuthorization($event)"
      >
        <div class="row">
          <div class="columns">
            <b-field
              v-for="(column, indexColumn) of columnsVisible"
              :key="indexColumn"
              :field="indexColumn"
              :label="getColumnTitle(column)"
              class="column"
              :style="!column.display ? 'display : contents' : ''"
            ></b-field>
          </div>
        </div>
      </AuthorizationTable>

      <div class="buttons">
        <b-button
          icon-left="plus"
          type="is-dark"
          @click="handleSubmit(createAuthorization)"
          style="margin-bottom: 10px"
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
import { LOCAL_STORAGE_LANG } from "@/services/Fetcher";
import { ReferenceService } from "@/services/rest/ReferenceService";
import AuthorizationTable from "@/components/common/AuthorizationTable";
import { Authorization } from "@/model/authorization/Authorization";
import { Authorizations } from "@/model/authorization/Authorizations";

@Component({
  components: {
    AuthorizationTable,
    PageView,
    SubMenu,
    CollapsibleTree,
    ValidationObserver,
    ValidationProvider,
  },
})
export default class DataTypeAuthorizationInfoView extends Vue {
  @Prop() dataTypeId;
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
  publicAuthorizations = [];
  ownAuthorizations = [];
  ownAuthorizationsColumnsByPath = {};
  authorizations = [];
  users = [];
  name = null;
  dataGroups = [];
  authorizationScopes = [];
  application = new ApplicationResult();
  selectedlabels = [];
  userLabels = [];
  isApplicationAdmin = false;
  openOnFocus = true;

  periods = {
    FROM_DATE: this.$t("dataTypeAuthorizations.from-date"),
    TO_DATE: this.$t("dataTypeAuthorizations.to-date"),
    FROM_DATE_TO_DATE: this.$t("dataTypeAuthorizations.from-date-to-date"),
    ALWAYS: this.$t("dataTypeAuthorizations.always"),
  };

  columnsVisible = {
    label: {
      title: "Label",
      display: true,
      internationalizationName: { fr: "Domaine", en: "Domain" },
    },
  };
  period = this.periods.FROM_DATE_TO_DATE;
  startDate = null;
  endDate = null;
  configuration = {};
  authReferences = {};
  subMenuPaths = [];
  repositury = null;
  selectedUsers = [];

  getColumnTitle(column) {
    if (column.display) {
      return (
        (column.internationalizationName && column.internationalizationName[this.$i18n.locale]) ||
        column.title
      );
    }
  }

  modifyAuthorization(event) {
    var authorization = this.authorization;
    var authorizations = authorization.authorizations[event.indexColumn] || [];
    for (const authorizationKeytoAdd in event.authorizations.toAdd) {
      authorizations.push(event.authorizations.toAdd[authorizationKeytoAdd]);
    }
    for (const authorizationKeytoDelete in event.authorizations.toDelete) {
      var toDeleteElement = event.authorizations.toDelete[authorizationKeytoDelete];
      authorizations = authorizations.filter((auth) => {
        return !new Authorization(auth).equals(
          toDeleteElement,
          this.authorizationScopes.map((scope) => scope.id)
        );
      });
    }
    authorization.authorizations[event.indexColumn] = authorizations;
    this.authorization = new Authorizations(
      authorization,
      this.authorizationScopes.map((as) => as.id)
    );
  }

  registerCurrentAuthorization(event) {
    var authorization = this.authorization;
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
          this.authorizationScopes.map((scope) => scope.id)
        )
      ) {
        return auth;
      } else {
        return authorizationToReplace;
      }
    });
    authorization.authorizations[event.indexColumn] = authorizations;
    this.authorization = new Authorizations(
      authorization,
      this.authorizationScopes.map((as) => as.id)
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
    try {
      this.application = await this.applicationService.getApplication(this.applicationName, [
        "CONFIGURATION",
        "DATATYPE",
      ]);
      this.configuration = this.application.configuration.dataTypes[this.dataTypeId];
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
        localDatatypeName: this.internationalisationService.localeDataTypeIdName(
          this.application,
          this.application.dataTypes[this.dataTypeId]
        ),
      };
      this.authorizations = this.configuration?.authorization?.authorizationScopes || [];
      this.repositury = this.application.dataTypes[this.dataTypeId].repository != null;
      const grantableInfos = await this.authorizationService.getAuthorizationGrantableInfos(
        this.applicationName,
        this.dataTypeId
      );
      let authorizationsForUser;
      ({
        authorizationScopes: this.authorizationScopes,
        dataGroups: this.dataGroups,
        users: this.users,
        authorizationsForUser: authorizationsForUser,
      } = grantableInfos);

      let auths = authorizationsForUser.authorizationResults.admin;
      if (JSON.parse(localStorage.getItem("authenticatedUser"))) {
        let ownAuthorizations = JSON.parse(
          localStorage.getItem("authenticatedUser")
        ).authorizations;
        this.isApplicationAdmin = ownAuthorizations.find((a) =>
          new RegExp(a).test(this.dataTypeId)
        );
      }
      if (!this.isApplicationAdmin) {
        for (const scope in auths) {
          this.ownAuthorizations = this.ownAuthorizations || [];
          let scopeAuthorizations = auths[scope];
          let scopeAuthorization = new Authorization(scopeAuthorizations);
          let path = scopeAuthorization.getPath(this.authorizationScopes.map((a) => a.id));
          if (this.ownAuthorizations.indexOf(path) === -1) {
            if (!this.ownAuthorizations.find((pa) => path.startWith(pa))) {
              this.ownAuthorizations = this.ownAuthorizations.filter((pa) => !pa.startWith(path));
              this.ownAuthorizations.push(path);
            }
          }
        }
      }
      for (const path of this.ownAuthorizations) {
        for (const scopeId in authorizationsForUser.authorizationByPath) {
          if (authorizationsForUser.authorizationByPath[scopeId]) {
            for (const pathKey in authorizationsForUser.authorizationByPath[scopeId]) {
              if (pathKey.startsWith(path) || path.startsWith(pathKey)) {
                let autorizedPath = pathKey.startsWith(path) ? path : pathKey;
                this.ownAuthorizationsColumnsByPath[autorizedPath] =
                  this.ownAuthorizationsColumnsByPath[autorizedPath] || [];
                this.ownAuthorizationsColumnsByPath[autorizedPath].push(scopeId);
              }
            }
          }
        }
      }
      this.columnsVisible = { ...this.columnsVisible, ...grantableInfos.columnsDescription };
      if (!this.repositury) {
        this.columnsVisible.publication = { ...this.columnsVisible.publication, display: false };
      }
      if (this.authorizationId != "new") {
        var authorizations = await this.authorizationService.getAuthorizations(
          this.applicationName,
          this.dataTypeId,
          this.authorizationId
        );

        this.publicAuthorizations = {};
        for (const authorizationKey in authorizations.publicAuthorizations) {
          let auths = authorizations.publicAuthorizations[authorizationKey];
          for (const scope in auths) {
            this.publicAuthorizations[scope] = this.publicAuthorizations[scope] || [];
            let scopeAuthorizations = auths[scope];
            for (const scopeAuthorizationsKey in scopeAuthorizations) {
              let scopeAuthorization = new Authorization(
                scopeAuthorizations[scopeAuthorizationsKey]
              );
              let path = scopeAuthorization.getPath2(this.authorizationScopes.map((a) => a.id));
              if (this.publicAuthorizations[scope].indexOf(path) === -1) {
                if (!this.publicAuthorizations[scope].find((pa) => path.startWith(pa))) {
                  this.publicAuthorizations[scope] = this.publicAuthorizations[scope].filter(
                    (pa) => !pa.startWith(path)
                  );
                  this.publicAuthorizations[scope].push(path);
                }
              }
            }
          }
        }
        authorizations = new Authorizations(
          authorizations,
          this.authorizationScopes.map((as) => as.id)
        );
        this.authorization = authorizations;
      } else {
        this.authorization = new Authorizations(
          { dataType: this.dataTypeId, applicationNameOrId: this.applicationName },
          this.authorizationScopes.map((as) => as.id)
        );
      }
      let currentAuthorizationUsers = this.authorization.users || [];
      this.selectedUsers = this.users
        .filter((user) => {
          return currentAuthorizationUsers.find((u) => {
            return u.id == user.id;
          });
        })
        .map((user) => user.id);
      for (let i = 0; i < this.selectedUsers.length; i++) {
        for (let j = 0; j < this.users.length; j++) {
          if (this.selectedUsers[i] === this.users[j].id) {
            this.selectedlabels.push(this.users[j].label);
          }
        }
      }
      for (let i = 0; i < this.users.length; i++) {
        if (!this.selectedlabels.includes(this.users[i].label))
          this.userLabels.push(this.users[i].label);
      }
      this.userLabels.sort();
      grantableInfos.authorizationScopes.reverse();
      let ret = {};
      for (let auth in grantableInfos.authorizationScopes) {
        let authorizationScope = grantableInfos.authorizationScopes[auth];
        let vc = this.authorizations[authorizationScope?.label];
        var reference =
          this.configuration.data[vc.variable].components[vc.component].checker.params.refType;
        let ref = await this.getOrLoadReferences(reference);
        ret[auth] = { references: ref, authorizationScope: authorizationScope.label };
      }
      let refs = Object.values(ret)
        .reduce(
          (acc, k) => [
            ...acc,
            ...k.references.referenceValues.reduce(
              (a, b) => [...a, ...b.hierarchicalReference.split(".")],
              acc
            ),
          ],
          []
        )
        .reduce((a, b) => {
          if (a.indexOf(b) < 0) {
            a.push(b);
          }
          return a;
        }, []);
      for (const refsKey in refs) {
        await this.getOrLoadReferences(refs[refsKey]);
      }
      var remainingAuthorizations = [];
      for (const key in ret) {
        let partition = await this.partitionReferencesValues(
          ret[key]?.references?.referenceValues,
          ret[key]?.authorizationScope
        );
        remainingAuthorizations[key] = partition;
      }
      if (!remainingAuthorizations.length) {
        remainingAuthorizations = [
          {
            __DEFAULT__: {
              authorizationScope: {
                id: "__DEFAULT__",
                localName: "root",
              },
              completeLocalName: "__.__",
              currentPath: "__.__",
              isLeaf: true,
              localName: "__.__fr",
              reference: {},
              referenceValues: {},
            },
          },
        ];
      }
      this.authReferences = remainingAuthorizations;
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  getFilteredTags(text) {
    this.userLabels = [];
    this.filteredTags = this.users.filter((option) => {
      return option.label.toString().toLowerCase().indexOf(text.toLowerCase()) >= 0;
    });
    for (let i = 0; i < this.filteredTags.length; i++) {
      if (!this.selectedlabels.includes(this.filteredTags[i].label)) {
        this.userLabels.push(this.filteredTags[i].label);
      }
    }
    this.userLabels.sort();
  }

  async partitionReferencesValues(
    referencesValues,
    authorizationScope,
    currentPath,
    currentCompleteLocalName
  ) {
    let returnValues = {};
    for (const referenceValue of referencesValues) {
      var previousKeySplit = currentPath ? currentPath.split(".") : [];
      var keys = referenceValue.hierarchicalKey.split(".");
      var references = referenceValue.hierarchicalReference.split(".");
      if (previousKeySplit.length == keys.length) {
        continue;
      }
      for (let i = 0; i < previousKeySplit.length; i++) {
        keys.shift();
        references.shift();
      }
      var key = keys.shift();
      let newCurrentPath = (currentPath ? currentPath + "." : "") + key;
      var reference = references.shift();
      let refValues = await this.getOrLoadReferences(reference);
      this.internationalisationService.getUserPrefLocale();
      let lang = localStorage.getItem(LOCAL_STORAGE_LANG);
      let localName = refValues.referenceValues.find((r) => r.naturalKey == key);
      if (localName?.values?.["__display_" + lang]) {
        localName = localName?.values?.["__display_" + lang];
      } else {
        localName = key;
      }
      if (!localName) {
        localName = key;
      }
      var completeLocalName =
        typeof currentCompleteLocalName === "undefined" ? "" : currentCompleteLocalName;
      completeLocalName = completeLocalName + (completeLocalName == "" ? "" : ",") + localName;
      let authPartition = returnValues[key] || {
        key,
        reference,
        authorizationScope,
        referenceValues: [],
        localName,
        isLeaf: false,
        currentPath: newCurrentPath,
        completeLocalName,
      };
      authPartition.referenceValues.push(referenceValue);
      returnValues[key] = authPartition;
    }
    for (const returnValuesKey in returnValues) {
      var auth = returnValues[returnValuesKey];
      let referenceValueLeaf = auth.referenceValues?.[0];
      if (
        auth.referenceValues.length <= 1 &&
        referenceValueLeaf.hierarchicalKey == auth.currentPath
      ) {
        returnValues[returnValuesKey] = {
          ...auth,
          authorizationScope,
          isLeaf: true,
          referenceValues: { ...referenceValueLeaf, authorizationScope },
        };
      } else {
        var r = await this.partitionReferencesValues(
          auth.referenceValues,
          authorizationScope,
          auth.currentPath,
          auth.completeLocalName
        );
        returnValues[returnValuesKey] = {
          ...auth,
          isLeaf: false,
          referenceValues: r,
        };
      }
    }
    return returnValues;
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
        ...this.authorization,
        dataType: this.dataTypeId,
        applicationNameOrId: this.applicationName,
      };
      for (let i = 0; i < this.selectedlabels.length; i++) {
        for (let j = 0; j < this.users.length; j++) {
          if (this.selectedlabels[i] === this.users[j].label) {
            this.selectedUsers.push(this.users[j].id);
          }
        }
      }
      authorizationToSend.usersId = this.selectedUsers;
      for (const scope in authorizationToSend.authorizations) {
        authorizationToSend.authorizations[scope] = authorizationToSend.authorizations[scope].map(
          (auth) => {
            var returnedAuth = new Authorization(auth);
            returnedAuth.intervalDates = {
              fromDay: returnedAuth.fromDay,
              toDay: returnedAuth.toDay,
            };
            returnedAuth.dataGroups = returnedAuth.dataGroups.map((dg) => dg.id || dg);
            return returnedAuth;
          }
        );
      }
      await this.authorizationService.createAuthorization(
        this.applicationName,
        this.dataTypeId,
        authorizationToSend
      );
      this.alertService.toastSuccess(this.$t("alert.create-authorization"));
      this.$router.push(
        `/applications/${this.applicationName}/dataTypes/${this.dataTypeId}/authorizations`
      );
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  emitUpdateAuthorization(event) {
    console.log(event);
    /* this.authorizationsTree = event.authorizationsTree;
     var authorizationsToSave = {};
     for (const type in event.authorizationsTree) {
       authorizationsToSave[type] = this.extractAuthorizations(event.authorizationsTree[type]);
     }
     this.authorizationsToSave = { ...authorizationsToSave };*/
  }

  extractAuthorizations(authorizationTree) {
    var authorizationArray = [];
    if (!authorizationTree || Object.keys(authorizationTree).length === 0) {
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
