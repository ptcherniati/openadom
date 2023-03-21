<template>
  <PageView class="with-submenu">
    <SubMenu
      :aria-label="$t('menu.aria-sub-menu')"
      :paths="subMenuPaths"
      :root="$t('titles.applications-page')"
      role="navigation"
    />
    <h1 class="title main-title">
      {{ $t("titles.references-authorizations") }}
    </h1>
    <div class="rows">
      <div v-if="canManageRights" class="row">
        <div class="columns">
          <div class="column is-offset-10 is-2">
            <b-button icon-left="plus" type="is-primary is-right" @click="addAuthorization">
              {{ $t("referencesAuthorizations.add-auhtorization") }}
            </b-button>
          </div>
        </div>
      </div>
      <b-table
        :data="authorizations"
        :is-focusable="true"
        :is-hoverable="true"
        :paginated="true"
        :per-page="perPage"
        :striped="true"
        class="row"
        height="100%"
      >
        <template #pagination>
          <b-pagination
            v-model="currentPage"
            :aria-current-label="$t('menu.aria-curent-page')"
            :aria-label="$t('menu.aria-pagination')"
            :aria-next-label="$t('menu.aria-next-page')"
            :aria-previous-label="$t('menu.aria-previous-page')"
            :current-page.sync="currentPage"
            :per-page="perPage"
            :rounded="true"
            :total="authorizations.length"
            order="is-centered"
            range-after="3"
            range-before="3"
            role="navigation"
            @change="changePage"
          />
        </template>
        <b-table-column
          :label="$t('dataTypeAuthorizations.name')"
          :searchable="true"
          b-table-column
          field="name"
          sortable
        >
          <template #searchable="props">
            <b-input
              v-model="props.filters[props.column.field]"
              :placeholder="$t('dataTypeAuthorizations.search')"
              icon="search"
              size="is-small"
            />
          </template>
          <template v-slot="props">
            {{ props.row.name }}
          </template>
        </b-table-column>
        <b-table-column
          v-slot="props"
          :label="$t('dataTypeAuthorizations.users')"
          b-table-column
          field="users"
          sortable
        >
          <template v-for="(user, idx) in props.row.users.map((use) => use.login)">
            <div v-bind:key="idx" class="columns">
              <b-tooltip :label="$t('dataTypeAuthorizations.showMore')" position="is-right">
                <a
                  class="show-check-details column is-half"
                  style="color: #006464ff; margin-left: 10px"
                  type="is-primary "
                  @click="showModalUser(user)"
                >
                  {{ user }}
                </a>
              </b-tooltip>
            </div>
          </template>
        </b-table-column>
        <b-table-column
          v-slot="props"
          :label="$t('dataTypeAuthorizations.roles')"
          b-table-column
          field="authorizations"
          sortable
        >
          <template v-for="(authorization, idx) in Object.keys(props.row.authorizations)">
            <div v-bind:key="idx" class="columns">
              <b-tooltip :label="$t('dataTypeAuthorizations.showMore')" position="is-right">
                <a
                  class="show-check-details column is-half"
                  style="color: #006464ff; margin-left: 10px"
                  type="is-primary "
                  @click="
                    showModalRole(
                      props.row.name,
                      authorization,
                      props.row.authorizations[authorization]
                    )
                  "
                >
                  {{ authorization }}
                </a>
              </b-tooltip>
            </div>
          </template>
        </b-table-column>
        <b-table-column v-slot="props" :label="$t('dataTypeAuthorizations.actions')" b-table-column>
          <b-button
            icon-left="times-circle"
            size="is-small"
            style="height: 1.5em; background-color: transparent; font-size: 1.45rem"
            type="is-danger is-light"
            @click="revoke(props.row.uuid)"
          >
          </b-button>
          <b-button
            icon-left="pen-square"
            onmouseout="this.style.color='';"
            onmouseover="this.style.color='rgba(255,140,0,0.5)'"
            outlined
            size="is-small"
            style="
              height: 1.5em;
              background-color: transparent;
              font-size: 1.45rem;
              border-color: transparent;
            "
            type="is-warning"
            @click="addAuthorization(props.row.uuid)"
          >
          </b-button>
        </b-table-column>
      </b-table>
    </div>
  </PageView>
</template>

<script>
import PageView from "../common/PageView.vue";
import SubMenu from "@/components/common/SubMenu.vue";
import { SubMenuPath } from "@/components/common/SubMenu";
import { AuthorizationService } from "@/services/rest/AuthorizationService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AlertService } from "@/services/AlertService";
import { InternationalisationService } from "@/services/InternationalisationService";

export default {
  name: "AuthorizationreferencesManagement",
  async created() {
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("referencesManagement.references").toLowerCase(),
        () => this.$router.push(`/applications/${this.applicationName}/references`),
        () => this.$router.push(`/applications`),
        () => this.$router.push(`/applications/${this.applicationName}/references`)
      ),
      new SubMenuPath(
        this.$t("titles.references-authorizations"),
        () => {
          this.$router.push(`/applications/${this.applicationName}/references/authorizations`);
        },
        () => this.$router.push(`/applications/${this.applicationName}/references`)
      ),
    ];
    this.init();
  },
  components: {
    PageView,
    SubMenu,
  },
  props: {
    applicationName: {},
  },
  data: () => {
    return {
      subMenuPaths: [],
      authorizationService: AuthorizationService.INSTANCE,
      authorizations: [],
      applicationService: ApplicationService.INSTANCE,
      internationalisationService: InternationalisationService.INSTANCE,
      alertService: AlertService.INSTANCE,
      canManageRights: false,
      // pagination
      offset: 0,
      currentPage: 1,
      perPage: 10,
    };
  },
  methods: {
    init: async function () {
      this.isLoading = true;
      try {
        this.application = await this.applicationService.getApplication(this.applicationName, [
          "CONFIGURATION",
          "REFERENCETYPE",
        ]);
        this.references = Object.values(
          this.internationalisationService.treeReferenceName(this.application)
        );
        this.application = {
          ...this.application,
          localName: this.internationalisationService.mergeInternationalization(this.application)
            .localName,
        };
        let authorizations = await this.authorizationService.getReferencesAuthorizations(
          this.applicationName,
          {
            offset: this.offset,
            limit: this.perPage,
          }
        );
        this.authorizations = authorizations.authorizationResults.filter(
          () =>
            authorizations.authorizationsForUser.isAdministrator ||
            authorizations.authorizationsForUser.authorizationResults.admin
        );
        let authorizationForUser = authorizations.authorizationsForUser;
        this.canManageRights =
          authorizationForUser.isAdministrator || authorizationForUser.authorizationResults.admin;
      } catch (error) {
        this.alertService.toastServerError(error);
      }
    },

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
    },
    async changePage(page) {
      this.offset = (page - 1) * this.perPage;
    },

    addAuthorization(uuid) {
      this.$router.push(
        `/applications/${this.applicationName}/references/authorizations/${
          typeof uuid === "string" ? uuid : "new"
        }`
      );
    },

    async revoke(id) {
      try {
        await this.authorizationService.revokeReferenceAuthorization(this.applicationName, id);
        this.alertService.toastSuccess(this.$t("alert.revoke-authorization"));
      } catch (error) {
        this.alertService.toastServerError(error);
      }
      window.location.reload();
    },
  },
};
</script>

<style scoped></style>
