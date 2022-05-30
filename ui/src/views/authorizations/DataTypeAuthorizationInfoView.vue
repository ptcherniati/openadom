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
      <ValidationProvider
        v-slot="{ errors, valid }"
        name="users"
        rules="required"
        vid="users"
        class="columns"
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
          <b-select
            v-model="usersToAuthorize"
            :placeholder="$t('dataTypeAuthorizations.users-placeholder')"
            expanded
            multiple
          >
            <option v-for="user in users" :key="user.id" :value="user.id">
              {{ user.label }}
            </option>
          </b-select>
        </b-field>

        <b-field
          :label="$t('dataTypeAuthorizations.name')"
          :message="errors[0]"
          :type="{
            'is-danger': errors && errors.length > 0,
            'is-success': valid,
          }"
          class="column mb-4"
        >
          <b-input v-model="name" />
        </b-field>
      </ValidationProvider>
      <AuthorizationTable
        v-if="dataGroups && authReferences && columnsVisible && authReferences[0]"
        :authReference="authReferences[0]"
        :authorization-scopes="authorizationScopes"
        :authorizations-tree="authorizationsTree"
        :columnsVisible="columnsVisible"
        :dataGroups="dataGroups"
        :authorizations-to-save="authorizationsToSave"
        :remaining-option="authReferences.slice && authReferences.slice(1, authReferences.length)"
        :required-authorizations="{}"
        class="rows"
        @add-authorization="emitUpdateAuthorization($event)"
        @delete-authorization="emitUpdateAuthorization($event)"
      >
        <div class="row">
          <div class="columns">
            <b-field
              v-for="(column, indexColumn) of columnsVisible"
              :key="indexColumn"
              :field="indexColumn"
              :label="column.title"
              class="column"
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
import { InternationalisationService } from "@/services/InternationalisationService";
import { ApplicationResult } from "@/model/ApplicationResult";
import { LOCAL_STORAGE_LANG } from "@/services/Fetcher";
import { ReferenceService } from "@/services/rest/ReferenceService";
import AuthorizationTable from "@/components/common/AuthorizationTable";
import { Authorization } from "@/model/authorization/Authorization";

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
  @Prop() authorizationId;

  __DEFAULT__ = "__DEFAULT__";
  referenceService = ReferenceService.INSTANCE;
  references = {};
  authorizationService = AuthorizationService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  userPreferencesService = UserPreferencesService.INSTANCE;
  authorizationsTree = {};
  checkbox = false;
  authorizations = [];
  users = [];
  name = null;
  dataGroups = [];
  authorizationScopes = [];
  application = new ApplicationResult();
  usersToAuthorize = [];
  dataGroupToAuthorize = null;
  openCollapse = null;
  scopesToAuthorize = {};

  periods = {
    FROM_DATE: this.$t("dataTypeAuthorizations.from-date"),
    TO_DATE: this.$t("dataTypeAuthorizations.to-date"),
    FROM_DATE_TO_DATE: this.$t("dataTypeAuthorizations.from-date-to-date"),
    ALWAYS: this.$t("dataTypeAuthorizations.always"),
  };

  columnsVisible = {
    label: { title: "Label", display: true },
    //dataGroups: {title: this.$t('dataTypeAuthorizations.data-groups'), display: true},
    extraction: { title: "Extraction", display: true },
    admin: { title: "Admin", display: true },
    depot: { title: "Dépôt", display: true },
    publication: { title: "Publication", display: true },
  };
  period = this.periods.FROM_DATE_TO_DATE;
  startDate = null;
  endDate = null;
  applications = [];
  configuration = {};
  ToAuthorize;
  authReferences = {};
  authorizationsToSave = {};

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
      this.applications = await this.applicationService.getApplications();
      this.application = await this.applicationService.getApplication(this.applicationName);
      this.configuration = this.applications
        .filter((a) => a.name === this.applicationName)
        .map((a) => a.configuration.dataTypes[this.dataTypeId])[0];
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
        localDatatypeName: this.internationalisationService.localeDataTypeIdName(
          this.application,
          this.application.dataTypes[this.dataTypeId]
        ),
      };
      this.authorizations = await this.authorizationService.getDataAuthorizations(
        this.applicationName,
        this.dataTypeId
      );
      this.authorizations = this.configuration?.authorization?.authorizationScopes || [];
      const grantableInfos = await this.authorizationService.getAuthorizationGrantableInfos(
        this.applicationName,
        this.dataTypeId
      );
      ({
        authorizationScopes: this.authorizationScopes,
        dataGroups: this.dataGroups,
        users: this.users,
      } = grantableInfos);
      grantableInfos.authorizationScopes.reverse();
      // this.authorizationScopes[0].options[0].children[0].children.push({
      //   children: [],
      //   id: "toto",
      //   label: "toto",
      // });
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

    /*this.authorizationsTree = {
      "publication": {
        "projet_atlantique": {
          "bassin_versant": {
            "nivelle": new Authorization(),
            "oir": new Authorization()
          },
          plateforme: new Authorization(),
        },
      },
      depot: {
        projet_manche: new Authorization(),
      },
      "depot": {
        "projet_manche": new Authorization()
        }
    };*/
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
    const dataTypeAuthorization = new DataTypeAuthorization();
    dataTypeAuthorization.usersId = this.usersToAuthorize;
    dataTypeAuthorization.applicationNameOrId = this.applicationName;
    dataTypeAuthorization.dataType = this.dataTypeId;
    dataTypeAuthorization.name = this.name;
    dataTypeAuthorization.authorizations = this.authorizationsToSave;

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

  emitUpdateAuthorization(event) {
    this.authorizationsTree = event.authorizationsTree;
    var authorizationsToSave = {};
    for (const type in event.authorizationsTree) {
      authorizationsToSave[type] = this.extractAuthorizations(event.authorizationsTree[type]);
    }
    this.authorizationsToSave = { ...authorizationsToSave };
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