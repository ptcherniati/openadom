<template>
  <PageView class="with-submenu">
    <SubMenu
      :aria-label="$t('menu.aria-sub-menu')"
      :paths="subMenuPaths"
      :root="application.localName || application.title"
      role="navigation"
    />

    <h1 class="title main-title">
      <span>{{
        $t(
          authorizationId == "new"
            ? `referencesAuthorizations.sub-menu-new-authorization`
            : "referencesAuthorizations.sub-menu-modify-authorization",
          { authorizationId }
        )
      }}</span>
    </h1>
    <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
      <div class="columns">
        <ValidationProvider
          v-slot="{ errors, valid }"
          class="column is-half"
          name="users"
          rules="required"
          vid="users"
        >
          <b-field
            :label="$t('referencesAuthorizations.users')"
            :message="errors[0]"
            :type="{
              'is-danger': errors && errors.length > 0,
              'is-success': valid,
            }"
            class="column mb-4"
          >
            <b-taginput
              v-model="selectedlabels"
              :data="userLabels"
              :open-on-focus="openOnFocus"
              :placeholder="$t('referencesAuthorizations.users-placeholder')"
              :value="userLabels"
              autocomplete
              expanded
              type="is-dark"
              @typing="getFilteredTags"
            >
            </b-taginput>
            <b-tooltip :label="$t('referencesAuthorizations.closeUser')" position="is-bottom">
              <b-button v-model="openOnFocus" icon-left="times-circle"> </b-button>
            </b-tooltip>
          </b-field>
        </ValidationProvider>
        <ValidationProvider
          v-slot="{ errors, valid }"
          class="column is-half"
          name="users"
          rules="required"
          vid="users"
        >
          <b-field
            :label="$t('referencesAuthorizations.name')"
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
      </div>
      <table>
        <thead>
          <tr>
            <th>référentiel</th>
            <th>Administration</th>
            <th>Gestion</th>
          </tr>
        </thead>
        <tbody></tbody>
        <tr v-for="(ref, index) in references" :key="index">
          <td>{{ ref.refNameLocal }}</td>
          <td>
            <b-checkbox v-model="ref.isAdmin" />
          </td>
          <td>
            <b-checkbox v-model="ref.isManage" />
          </td>
        </tr>
      </table>

      <div class="buttons">
        <b-button
          icon-left="plus"
          style="margin-bottom: 10px"
          type="is-dark"
          @click="handleSubmit(createOrUpdateAuthorization)"
        >
          {{
            authorization
              ? $t("referencesAuthorizations.modify")
              : $t("referencesAuthorizations.create")
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
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { InternationalisationService } from "@/services/InternationalisationService";
import { ApplicationResult } from "@/model/ApplicationResult";
import { ReferenceService } from "@/services/rest/ReferenceService";

@Component({
  components: {
    PageView,
    SubMenu,
    CollapsibleTree,
    ValidationObserver,
    ValidationProvider,
  },
})
export default class ReferencesAuthorizationInfoView extends Vue {
  @Prop() dataTypeId;
  @Prop() applicationName;
  @Prop({ default: "new" }) authorizationId;

  __DEFAULT__ = "__DEFAULT__";
  referenceService = ReferenceService.INSTANCE;
  references = {};
  openOnFocus = false;
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
  isLoading;

  openOnFocus = true;
  authReferences = {};
  subMenuPaths = [];
  selectedUsers = [];
  filteredTags = [];

  getColumnTitle(column) {
    if (column.display) {
      return (
        (column.internationalizationName && column.internationalizationName[this.$i18n.locale]) ||
        column.title
      );
    }
  }

  async created() {
    this.init();
    this.chosenLocale = this.userPreferencesService.getUserPrefLocale();
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("referencesManagement.references").toLowerCase(),
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`),
        () => this.$router.push("/applications")
      ),
      new SubMenuPath(
        this.$t(`referencesAuthorizations.sub-menu-reference-authorizations`),
        () => {
          this.$router.push(`/applications/${this.applicationName}/references/authorizations`);
        },
        () => this.$router.push(`/applications/${this.applicationName}/references`)
      ),
      new SubMenuPath(
        this.$t(
          this.authorizationId == "new"
            ? `referencesAuthorizations.sub-menu-new-authorization`
            : "referencesAuthorizations.sub-menu-modify-authorization",
          { authorizationId: this.authorizationId }
        ),
        () => {},
        () => {
          this.$router.push(`/applications/${this.applicationName}/references/authorizations`);
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
        "REFERENCETYPE",
      ]);
      let params = {
        userId: null,
      };
      if ("new" != this.authorizationId) {
        params = { ...params, authorizationId: this.authorizationId };
      } else {
        params = { ...params, limit: 0 };
      }
      let authorizations = await this.authorizationService.getReferencesAuthorizations(
        this.applicationName,
        params
      );
      let authorizationForUser = authorizations.authorizationsForUser;
      this.users = authorizations.users;
      this.authorizations = authorizations;
      this.authorization = authorizations.authorizationResults?.[0];
      this.name = this.authorization?.name;
      let configuration = Object.values(
        this.internationalisationService.treeReferenceName(this.application)
      );
      let references = {};
      for (const configurationCode in configuration) {
        if (
          authorizationForUser.isAdministrator ||
          authorizationForUser.authorizationResults?.admin?.includes(
            configuration[configurationCode].label
          )
        ) {
          let isAdmin =
            this.authorization &&
            (this.authorization?.authorizations?.admin || []).includes(
              configuration[configurationCode].label
            );
          let isManage =
            this.authorization &&
            (this.authorization?.authorizations?.manage || []).includes(
              configuration[configurationCode].label
            );
          references[configurationCode] = {
            ...configuration[configurationCode],
            isAdmin,
            isManage,
          };
        }
      }
      this.references = references;

      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
        localReferencesNames: Object.values(
          this.internationalisationService.treeReferenceName(this.application)
        ),
      };
      let currentAuthorizationUsers = (this.authorization && this.authorization.users) || [];
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

  async createOrUpdateAuthorization() {
    try {
      let users = this.selectedlabels
        .reduce((acc, label) => {
          acc.push(this.users.find((u) => u.label == label));
          return acc;
        }, [])
        .map((u) => u.id);
      let references = Object.values(this.references).reduce((acc, ref) => {
        if (ref.isAdmin) {
          let isAdmin = acc.admin || [];
          isAdmin.push(ref.label);
          acc.admin = isAdmin;
        }
        if (ref.isManage) {
          let isManage = acc.manage || [];
          isManage.push(ref.label);
          acc.manage = isManage;
        }
        return acc;
      }, {});
      //let references = this.
      let authorization = {
        usersId: users,
        applicationNameOrId: this.applicationName,
        uuid: "new" == this.authorizationId ? null : this.authorizationId,
        name: this.name,
        references: references,
      };
      this.authorizationService.createOrUpdateReferencesAuthorization(authorization);
      this.$router.push(`/applications/${this.applicationName}/references/authorizations`);
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
