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
          v-if="currentUser.authorizedForApplicationCreation"
          field="applications"
          :label="'Applications'"
          width="300"
          sortable
          v-slot="props"
        >
          <template>
            <div class="columns">
              <b-field class="column is-2" v-for="auth in props.row.authorizations" :key="auth">
                <p style="margin-top: 8px">{{ auth }} {{ $t("ponctuation.colon") }}</p>
                <b-button
                  v-if="
                    props.row.authorizations.find((a) =>
                      currentUserApplicationPattern.find((aa) => new RegExp(aa).test(a))
                    )
                  "
                  icon-left="times-circle"
                  size="is-small"
                  type="is-danger is-light"
                  @click="removeApplication(props.row.login, auth)"
                  style="height: 1.5em; background-color: transparent; font-size: 1.45rem"
                >
                </b-button>
                <b-button
                  v-else
                  size="is-small"
                  type="is-danger is-light"
                  style="height: 1.5em; background-color: transparent; font-size: 1.45rem"
                >
                </b-button>
              </b-field>
              <b-field
                class="column is-2"
                v-for="auth in currentUserApplicationPattern.filter(
                  (a) => !props.row.authorizations.find((aa) => aa === a)
                )"
                :key="auth"
              >
                <p style="margin-top: 8px" class="has-text-grey-light">
                  {{ auth }} {{ $t("ponctuation.colon") }}
                </p>
                <b-button
                  icon-left="pen-square"
                  size="is-small"
                  type="primary is-light"
                  style="height: 1.5em; background-color: transparent; font-size: 1.45rem"
                  @click="addApplication(props.row.login, auth)"
                >
                </b-button>
              </b-field>
            </div>
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
import DataTypeAuthorizationInfoView from "@/views/authorizations/DataTypeAuthorizationInfoView";

@Component({
  components: { DataTypeAuthorizationInfoView, PageView, SubMenu },
})
export default class AuthorizationManagementForApplicationCreatorView extends Vue {
  subMenuPaths = [];
  authorizationService = AuthorizationService.INSTANCE;
  authorizations = [];
  currentUser = localStorage.getItem("authenticatedUser");
  currentUserApplicationPattern = localStorage.getItem("authenticatedUser");
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
    this.currentUserApplicationPattern = this.currentUser.authorizations;
    this.changes = {
      administrator: { add: [], remove: [] },
      applications: {},
    };
    var authorizations = await this.authorizationService.getAuthorizations();
    authorizations = authorizations.map((authorization) => {
      authorization.authorizations = authorization.authorizations.filter(this.filterAuthorization);
      return authorization;
    });
    console.log("authorizations", authorizations);
    this.authorizations = authorizations;
  }

  filterAuthorization(authorization) {
    return this.currentUserApplicationPattern.find(
      (auth) => auth === authorization || new RegExp(authorization).test(auth)
    );
  }

  async created() {
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("titles.authorizations-management"),
        () => {
          this.$router.push(`/authorizationsManagementForApplicationCreator`);
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
    for (const userId of this.changes.administrator.add) {
      await this.authorizationService.createAuthorizedRole(
        "authorizedForApplicationCreation",
        userId
      );
    }
    for (const userId of this.changes.administrator.remove) {
      await this.authorizationService.revokeAuthorizedRole(
        "authorizedForApplicationCreation",
        userId
      );
    }
    for (const userId in this.changes.applications) {
      if (this.changes.applications[userId].add) {
        for (const applicationPattern of this.changes.applications[userId].add) {
          await this.authorizationService.createAuthorizedRole(
            "applicationCreator",
            userId,
            applicationPattern
          );
        }
      }
      if (this.changes.applications[userId].remove) {
        for (const applicationPattern of this.changes.applications[userId].remove) {
          await this.authorizationService.revokeAuthorizedRole(
            "applicationCreator",
            userId,
            applicationPattern
          );
        }
      }
    }
  }

  changed(user, value) {
    let style = "";
    let changes = this.changes.applications[user];
    if (changes) {
      let add = changes.add;
      add = add && add.filter((v) => v === value).length;
      let remove = changes.remove;
      remove = remove && remove.filter((v) => v === value).length;
      style = add || remove ? "hasBorder " : "";
    }
    return style;
  }

  addApplication(user, value) {
    this.authorizations = this.authorizations.map((authorization) => {
      if (authorization.login === user) {
        authorization.authorizations.push(value);
      }
      return authorization;
    });

    if (this.changes.applications[user]?.remove?.find((v) => v === value)) {
      this.changes.applications[user].remove = this.changes.applications[user].remove.filter(
        (v) => v !== value
      );
    } else {
      this.changes.applications[user] = this.changes.applications[user] || {};
      this.changes.applications[user].add = this.changes.applications[user].add || [];
      this.changes.applications[user].add.push(value);
    }
  }

  removeApplication(user, value) {
    this.authorizations = this.authorizations.map((authorization) => {
      if (authorization.login === user) {
        let a = authorization.authorizations.filter((aa) => aa !== value);
        authorization.authorizations = a;
      }
      return authorization;
    });
    if (this.changes.applications[user]?.add?.find((v) => v === value)) {
      this.changes.applications[user].add = this.changes.applications[user].add.filter(
        (v) => v !== value
      );
    } else {
      this.changes.applications[user] = this.changes.applications[user] || {};
      this.changes.applications[user].remove = this.changes.applications[user].remove || [];
      this.changes.applications[user].remove.push(value);
    }
  }
}
</script>
<style lang="scss" scoped>
.hasBorder {
  border: solid #fb0738 6px;
}
</style>
