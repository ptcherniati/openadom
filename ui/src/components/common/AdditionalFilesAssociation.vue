<template>
  <li class="card-content authorizationTable datepicker-row">
    <div>
      <div v-if="canShowLine">
        <div class="columns" @mouseleave="upHere = false" @mouseover="upHere = true">
          <div class="column">
            <a
              field="label"
              class="leaf"
              style="min-height: 10px; display: table-cell; vertical-align: middle"
              @click="showDetail = !showDetail"
            >
              {{ dataTypeDescription.localName }}
            </a>
          </div>
          <div class="column">
            <b-icon
              :icon="STATES[state]"
              class="is-centered"
              @click.native="selectCheckbox"
              pack="far"
              size="is-medium"
              type="is-primary"
            />
          </div>
        </div>
      </div>
    </div>
    <AuthorizationTable
      v-if="showDetail && authReferences && columnsVisible && authReferences[0]"
      :auth-reference="authReferences[0]"
      :authorization-scopes="authorizationScopes"
      :columns-visible="columnsVisible"
      :data-groups="[]"
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
      @setIndetermined="setIndetermined($event)"
      @registerCurrentAuthorization="registerCurrentAuthorization($event)"
    >
    </AuthorizationTable>
  </li>
</template>

<script>
import AuthorizationTable from "@/components/common/AuthorizationTable";
import { Authorization } from "@/model/authorization/Authorization";
import { Authorizations } from "@/model/authorization/Authorizations";
import { AuthorizationService } from "@/services/rest/AuthorizationService";
import { ReferenceService } from "@/services/rest/ReferenceService";
import { LOCAL_STORAGE_LANG } from "@/services/Fetcher";
import { InternationalisationService } from "@/services/InternationalisationService";

export default {
  watch: {
    authorizationsToSet(associate) {
      if (!associate.length || associate[0].dataType != this.dataType) {
        return;
      }
      let associateResultElement = { authorizations: { associate: [] }, scopes: { associate: {} } };
      for (const associatesKey in associate) {
        let assos = associate[associatesKey];
        associateResultElement = associateResultElement || {};
        associateResultElement.applicationNameOrId = assos.application;
        associateResultElement.dataType = assos.dataType;
        associateResultElement.name = "";
        associateResultElement.users = [];
        let authorizations = associateResultElement.authorizations.associate;
        for (const authKey in assos.authorizations ? assos.authorizations.associate : {}) {
          let auth = assos.authorizations.associate[authKey];
          let authResult = {};
          authResult.dataGroups = auth.dataGroups;
          authResult.from = auth.from;
          authResult.to = auth.to;
          authResult.requiredAuthorizations = {};
          for (const scopeKey in auth.requiredAuthorizations) {
            authResult.requiredAuthorizations[scopeKey] = auth.requiredAuthorizations[scopeKey].sql;
          }
          authResult = new Authorization(authResult);
          authorizations.push(authResult);
        }
      }
      this.authorization = associateResultElement;
      this.updateAuthorizations();
      this.showDetail = false;
      this.updateState();
      this.$forceUpdate();
    },
  },
  name: "AdditionalFilesAssociation",
  components: {
    AuthorizationTable,
  },
  props: {
    dataType: {
      type: String,
      required: true,
    },
    applications: {
      type: Array,
      required: true,
    },
    applicationName: {
      type: String,
      required: true,
    },
    authorizationId: {
      type: String,
      required: false,
      default: "new",
    },
    configuration: {
      type: Object,
      required: true,
    },
    authorizationsToSet: {
      type: Array,
      required: false,
      default: () => [],
    },
    columnsVisible: {
      type: Object,
      required: true,
    },
    datatypeAuthorization: {
      type: Object,
      required: false,
    },
    dataTypeDescription: {
      type: Object,
      required: true,
    },
    grantableInfos: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      authorization: null,
      authorizationsForUser: {},
      authorizationService: AuthorizationService.INSTANCE,
      referenceService: ReferenceService.INSTANCE,
      internationalisationService: InternationalisationService.INSTANCE,
      authorizationScopes: [],
      publicAuthorizations: {},
      ownAuthorizations: [],
      ownAuthorizationsColumnsByPath: {},
      isApplicationAdmin: false,
      authReferences: {},
      STATES: { "-1": "square-minus", 0: "square", 1: "square-check" },
      canShowLine: true,
      upHere: false,
      showDetail: false,
      state: 0,
    };
  },
  emits: ["modifyAssociates", "update:refValues"],
  created() {
    this.init();
  },
  methods: {
    groupsAuthorizations(auths, ...args) {
      args.forEach((arg) => {
        if (arg) {
          arg
            .filter((pub) => auths.indexOf(pub.path) < 0)
            .forEach((pub) => {
              auths.push(pub);
              this.ownAuthorizationsColumnsByPath[pub.path] =
                this.ownAuthorizationsColumnsByPath[pub.path] || [];
              if (this.ownAuthorizationsColumnsByPath[pub.path].indexOf("associate") < 0) {
                this.ownAuthorizationsColumnsByPath[pub.path].push("associate");
                this.ownAuthorizations.push(pub.path);
              }
            });
        }
      });
    },
    async init() {
      this.authorizations = this.configuration?.authorization?.authorizationScopes || [];
      const grantableInfos = await this.authorizationService.getAuthorizationGrantableInfos(
        this.applicationName,
        this.dataType
      );
      let configuration = this.applications
        .filter((a) => a.name === this.applicationName)
        .map((a) => a.configuration.dataTypes[this.dataType])[0];
      let authorizationsForUser;
      ({
        authorizationScopes: this.authorizationScopes,
        dataGroups: this.dataGroups,
        users: this.users,
        authorizationsForUser: authorizationsForUser,
        publicAuthorizations: this.publicAuthorizations,
      } = grantableInfos);
      let auths = authorizationsForUser.authorizationResults.admin || [];
      if (!this.isApplicationAdmin) {
        this.groupsAuthorizations(
          auths,
          authorizationsForUser.authorizationResults.publication,
          authorizationsForUser.authorizationResults.depot,
          this.publicAuthorizations.publication,
          this.publicAuthorizations.depot
        );
        if (JSON.parse(localStorage.getItem("authenticatedUser"))) {
          let ownAuthorizations = JSON.parse(
            localStorage.getItem("authenticatedUser")
          ).authorizations;
          this.isApplicationAdmin = ownAuthorizations.find((a) =>
            new RegExp(a).test(this.dataType)
          );
        }
        for (const scope in auths) {
          this.ownAuthorizations = this.ownAuthorizations || [];
          //auths.forEach()
          let scopeAuthorizations = auths[scope];
          let scopeAuthorization = new Authorization(scopeAuthorizations);
          let path = scopeAuthorization.getPath(this.authorizationScopes.map((a) => a.id));
          if (this.ownAuthorizations.indexOf(path) === -1) {
            if (!this.ownAuthorizations.find((pa) => this.comparePathes(path, pa))) {
              this.ownAuthorizations = this.ownAuthorizations.filter(
                (pa) => !this.comparePathes(pa, path)
              );
              this.ownAuthorizations.push(path);
            }
          }
        }
      }
      this.authorizationsForUser = authorizationsForUser;
      this.updateAuthorizations();
      grantableInfos.authorizationScopes.reverse();
      let ret = {};
      for (let auth in grantableInfos.authorizationScopes) {
        let authorizationScope = grantableInfos.authorizationScopes[auth];
        let vc = (configuration?.authorization?.authorizationScopes || [])[
          authorizationScope?.label
        ];
        var reference =
          configuration.data[vc.variable].components[vc.component].checker.params.refType;
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
    },
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
    },
    comparePathes(path1, path2) {
      return (path1 || "").startsWith(path2);
    },
    updateAuthorizations() {
      for (const path of this.ownAuthorizations) {
        for (const scopeId in this.authorizationsForUser.authorizationByPath) {
          if (this.authorizationsForUser.authorizationByPath[scopeId]) {
            for (const pathKey in this.authorizationsForUser.authorizationByPath[scopeId]) {
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
      let publicAuthorizations = this.publicAuthorizations;
      this.publicAuthorizations = {};
      for (const authorizationKey in publicAuthorizations) {
        let auths = publicAuthorizations[authorizationKey].authorizations;
        for (const scope in auths) {
          this.publicAuthorizations[scope] = this.publicAuthorizations[scope] || [];
          let scopeAuthorizations = auths[scope];
          for (const scopeAuthorizationsKey in scopeAuthorizations) {
            let scopeAuthorization = new Authorization(scopeAuthorizations[scopeAuthorizationsKey]);
            let path = scopeAuthorization.getPath2(this.authorizationScopes.map((a) => a.id));
            if (this.publicAuthorizations[scope].indexOf(path) === -1) {
              if (
                !this.publicAuthorizations[scope].find((pa) => {
                  return this.comparePathes(path, pa);
                })
              ) {
                this.publicAuthorizations[scope] = this.publicAuthorizations[scope].filter(
                  (pa) => !this.comparePathes(pa, path)
                );
                this.publicAuthorizations[scope].push(path);
              }
            }
          }
        }
      }
      if (this.authorization) {
        let authorizations = new Authorizations(
          this.authorization,
          this.authorizationScopes.map((as) => as.id)
        );
        this.authorization = authorizations;
      } else {
        this.authorization = new Authorizations(
          { dataType: this.dataType, applicationNameOrId: this.applicationName },
          this.authorizationScopes.map((as) => as.id)
        );
      }
    },
    async getOrLoadReferences(reference) {
      if (this.refValues[reference]) {
        return this.refValues[reference];
      }
      let ref = await this.referenceService.getReferenceValues(this.applicationName, reference);
      this.refValues[reference] = ref;
      this.$emit("update:refValues", this.refValues);
      return ref;
    },
    selectCheckbox() {
      if (this.state != 0) {
        this.authorization.authorizations.associate = [];
        this.state = 0;
        this.authorization = new Authorizations(this.authorization, this.authorizationScopes);
      } else if (this.authorizationScopes?.[0]) {
        let toAdd = [];
        for (let requiredAuthorizationPath in this.authReferences[0]) {
          let requiredAuthorization = {};
          requiredAuthorization[this.authorizationScopes[0].id] = requiredAuthorizationPath;
          let authorization = new Authorization(null, requiredAuthorization, null, null);
          toAdd.push(authorization);
        }
        this.authorization.authorizations.associate = toAdd;
        this.state = 1;
      }
      // eslint-disable-next-line no-self-assign
      this.authorization = new Authorizations(this.authorization, this.authorizationScopes);

      this.$emit("modifyAssociates", {
        dataType: this.dataType,
        associates: this.authorization,
      });
    },

    setIndetermined(event) {
      var authorization = this.authorization;
      var authorizations = event.authorizations.toAdd;
      authorization.authorizations.associate = authorizations;
      this.authorization = new Authorizations(
        authorization,
        this.authorizationScopes.map((as) => as.id)
      );
      this.state = -1;
      this.$emit("modifyAssociates", {
        dataType: this.dataType,
        associates: this.authorization,
      });
    },

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
      authorization.authorizations.associate = authorizations;
      this.authorization = new Authorizations(
        authorization,
        this.authorizationScopes.map((as) => as.id)
      );
      this.updateState();
      this.$emit("modifyAssociates", {
        dataType: this.dataType,
        associates: this.authorization,
      });
    },
    updateState() {
      this.state =
        this.authorization.authorizations.associate &&
        this.authorization.authorizations.associate.length
          ? 1
          : 0;
      if (this.state) {
        let scopeId = this.authorizationScopes.length && this.authorizationScopes[0].id;
        for (const authReferenceKey in this.authReferences[0]) {
          if (
            !this.authorization.authorizations.associate ||
            !this.authorization.authorizations.associate.find((auth) => {
              let requiredAuthorization = auth.requiredAuthorizations[scopeId];
              return authReferenceKey == requiredAuthorization;
            })
          ) {
            this.state = -1;
          }
        }
      }
    },
  },
};
</script>

<style lang="scss" scoped>
.authorizationTable {
  margin-left: 10px;
  padding: 0 0 0 5px;

  button {
    opacity: 0.75;
  }

  .dropdown-menu .dropdown-content .dropDownMenu button {
    opacity: 0.5;
  }

  dgSelected {
    color: #007f7f;
  }
}

a {
  color: $dark;
  font-weight: bold;
  text-decoration: underline;
}

a:hover {
  color: $primary;
  text-decoration: none;
}

p {
  font-weight: bold;
}

::marker {
  color: transparent;
}

.column {
  padding: 6px;
}

.show-check-details {
  margin-left: 0.6em;
}

.show-detail-for-selected {
  height: 60px;
}
</style>
