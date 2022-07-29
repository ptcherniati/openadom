<template>
  <PageView class="with-submenu">
    <SubMenu
      :paths="subMenuPaths"
      :root="$t('titles.applications-page')"
      role="navigation"
      :aria-label="$t('menu.aria-sub-menu')"
    />
    <h1 class="title main-title">
      {{ $t("titles.authorizations-management") }}
    </h1>
    <div class="rows">
      <b-table
        class="row"
        :data="authorizations"
        paginated
        :current-page="currentPage"
        per-page="15"
      >
        <template #pagination>
          <b-pagination
            v-model="currentPage"
            :current-page.sync="currentPage"
            per-page="15"
            :total="authorizations.length"
            role="navigation"
            :aria-label="$t('menu.aria-pagination')"
            :aria-current-label="$t('menu.aria-curent-page')"
            :aria-next-label="$t('menu.aria-next-page')"
            :aria-previous-label="$t('menu.aria-previous-page')"
            order="is-centered"
            range-after="3"
            range-before="3"
            :rounded="true"
          />
        </template>
        <b-table-column
          searchable
          field="admin"
          :label="'login'"
          width="300"
          sortable
          v-slot="props"
          :custom-search="search"
        >
          <template>
            {{ props.row.login }}
          </template>
        </b-table-column>
        <b-table-column
          v-if="currentUser.superadmin"
          field="administrator"
          :label="'Administration'"
          width="300"
          sortable
          v-slot="props"
        >
          <template>
            <b-checkbox v-model="props.row.superadmin" @input="selectAdmin($event, props.row)" />
          </template>
        </b-table-column>
        <b-table-column
          v-if="currentUser.authorizedForApplicationCreation"
          field="applications"
          :label="'Applications'"
          width="300"
          sortable
          v-slot="props"
        >
          <template>
            <b-taginput
              class="inputAuth"
              :before-adding="beforeAdding"
              v-model="props.row.authorizations"
              @add="addApplication($event, props.row)"
              @remove="removeApplication($event, props.row)"
              ellipsis
              type="is-dark"
              icon="file"
              :placeholder="$t('dataTypeAuthorizations.add-application-name')"
              aria-close-label="Supprimer l'application"
            >
            </b-taginput>
          </template>
        </b-table-column>
      </b-table>
      <div class="row">
        <div class="column is-offset-10 is-2">
          <b-button
            icon-left="floppy-disk"
            icon-pack="far"
            type="is-primary is-right"
            @click="registerChanges"
          >
            {{ $t("dataTypeAuthorizations.save") }}
          </b-button>
        </div>
      </div>
    </div>
  </PageView>
</template>

<script>
import SubMenu from "@/components/common/SubMenu.vue";

import { Component, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { SubMenuPath } from "@/components/common/SubMenu";
import { AuthorizationService } from "@/services/rest/AuthorizationService";

@Component({
  components: { PageView, SubMenu },
})
export default class AuthorizationManagementView extends Vue {
  subMenuPaths = [];
  authorizationService = AuthorizationService.INSTANCE;
  authorizations = [];
  totalRows = -1;
  currentPage = 1;

  search(user, search) {
    console.log("search", user, search);
    return user.login.match(search);
  }

  columns = [{ field: "login", label: "login" }, "administrateur", "applications"];
  changes = {
    administrator: { add: [], remove: [] },
    applications: {},
  };
  currentUser = JSON.parse(localStorage.getItem("authenticatedUser"));

  async init() {
    this.changes = {
      administrator: { add: [], remove: [] },
      applications: {},
    };
    this.authorizations = await this.authorizationService.getAuthorizations();
  }

  async created() {
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("titles.authorizations-management"),
        () => {
          this.$router.push(`/authorizationsManagement`);
        },
        () => this.$router.push(`/applications`)
      ),
    ];
    this.init();
  }

  async registerChanges() {
    await this.makeChanges();
    await this.init();
  }

  async makeChanges() {
    this.changes.administrator.add.forEach((userId) => {
      this.authorizationService.createAuthorizedRole("superadmin", userId);
    });
    this.changes.administrator.remove.forEach((userId) => {
      this.authorizationService.revokeAuthorizedRole("superadmin", userId);
    });
    for (const userId in this.changes.applications) {
      if (this.changes.applications[userId].add) {
        this.changes.applications[userId].add.forEach((applicationPattern) => {
          this.authorizationService.createAuthorizedRole(
            "applicationCreator",
            userId,
            applicationPattern
          );
        });
      }
    }
    for (const userId in this.changes.applications) {
      if (this.changes.applications[userId].remove) {
        this.changes.applications[userId].remove.forEach((applicationPattern) => {
          this.authorizationService.revokeAuthorizedRole(
            "applicationCreator",
            userId,
            applicationPattern
          );
        });
      }
    }
  }

  selectAdmin(isAdmin, user) {
    console.log("add", this.changes.administrator.add, "remove", this.changes.administrator.remove);
    if (isAdmin) {
      if (this.changes.administrator.remove.find((v) => v === user.id)) {
        this.changes.administrator.remove = this.changes.administrator.remove.filter(
          (v) => v === v.id
        );
      } else if (!this.changes.administrator.add.find((v) => v === user.id)) {
        this.changes.administrator.add.push(user.id);
      }
    } else {
      if (this.changes.administrator.add.find((v) => v === user.id)) {
        this.changes.administrator.add = this.changes.administrator.add.filter((v) => v === v.id);
      } else if (!this.changes.administrator.remove.find((v) => v === user.id)) {
        this.changes.administrator.remove.push(user.id);
      }
    }
  }

  addApplication(value, user) {
    console.log("adding " + value);
    if (this.changes.applications[user.id]?.remove?.find((v) => v === value)) {
      this.changes.applications[user.id].remove = this.changes.applications[user.id].remove.filter(
        (v) => v !== value
      );
    } else {
      this.changes.applications[user.id] = this.changes.applications[user.id] || {};
      this.changes.applications[user.id].add = this.changes.applications[user.id].add || [];
      this.changes.applications[user.id].add.push(value);
    }
  }

  removeApplication(value, user) {
    if (this.changes.applications[user.id]?.add?.find((v) => v === value)) {
      this.changes.applications[user.id].add = this.changes.applications[user.id].add.filter(
        (v) => v !== value
      );
    } else {
      this.changes.applications[user.id] = this.changes.applications[user.id] || {};
      this.changes.applications[user.id].remove = this.changes.applications[user.id].remove || [];
      this.changes.applications[user.id].remove.push(value);
    }
  }

  beforeAdding(value) {
    console.log("before adding " + value);
    return value;
  }
}
</script>
<style lang="scss" scoped>
.icon {
  font-size: 0.5rem;
}
</style>
