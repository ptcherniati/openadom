<template>
  <PageView class="with-submenu">
    <SubMenu
      :paths="subMenuPaths"
      :root="application.localName || application.title"
      role="navigation"
      :aria-label="$t('menu.aria-sub-menu')"
    />
    <h1 class="title main-title">
      {{
        $t("titles.data-type-authorizations", {
          dataType: application.localDatatypeName || dataTypeId,
        })
      }}
    </h1>
    <div class="rows">
      <div class="row">
        <div class="columns">
          <div class="card column is-2">
            <b-button icon-left="plus" type="is-primary is-right" @click="addAuthorization">
              {{ $t("dataTypeAuthorizations.add-auhtorization") }}
            </b-button>
          </div>
        </div>
      </div>

      <b-table
          v-if="authorizations"
          :data="authorizations"
          :isFocusable="true"
          :isHoverable="true"
          :paginated="true"
          :per-page="15"
          :sticky-header="true"
          :striped="true"
          class="row"
          height="100%"
      >
        <!--b-table-column
            v-slot="props"
            :label="$t('dataTypeAuthorizations.user')"
            b-table-column
            field="user"
            sortable
        >
          {{ props.row.user }}
        </b-table-column-->

        <b-table-column
            v-slot="props"
            :label="$t('dataTypeAuthorizations.name')"
            b-table-column
            field="name"
            sortable
        >
          {{ props.row.name }}
        </b-table-column>
        <b-table-column
            v-slot="props"
            :label="$t('dataTypeAuthorizations.roles')"
            b-table-column
            field="authorizations"
            sortable
        >
          {{Object.keys( props.row.authorizations || {} ) }}
        </b-table-column>
        <b-table-column
            v-slot="props"
            :label="$t('dataTypeAuthorizations.users')"
            b-table-column
            field="users"
            sortable
        >
          {{ props.row.users.map(use=>use.login) }}
        </b-table-column>
        <b-table-column v-slot="props" :label="$t('dataTypeAuthorizations.actions')" b-table-column>
          <b-button
            icon-left="trash-alt"
            size="is-small"
            type="is-danger"
            @click="revoke(props.row.id)"
          >
            {{ $t("dataTypeAuthorizations.revoke") }}
          </b-button>
        </b-table-column>
      </b-table>
      <b-pagination
        v-if="selectedUser && perPage <= selectedUser.length"
        v-model="currentPage"
        :per-page="perPage"
        :total="selectedUser.length"
        role="navigation"
        :aria-label="$t('menu.aria-pagination')"
        :aria-current-label="$t('menu.aria-curent-page')"
        :aria-next-label="$t('menu.aria-next-page')"
        :aria-previous-label="$t('menu.aria-previous-page')"
        order="is-centered"
        range-after="3"
        range-before="3"
        :rounded="true"
      >
      </b-pagination>
    </div>
  </PageView>
</template>

<script>
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AuthorizationService } from "@/services/rest/AuthorizationService";
import { InternationalisationService } from "@/services/InternationalisationService";
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { ApplicationResult } from "@/model/ApplicationResult";

@Component({
  components: { PageView, SubMenu },
})
export default class DataTypeAuthorizationsView extends Vue {
  @Prop() dataTypeId;
  @Prop() applicationName;toList

  authorizationService = AuthorizationService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  selectedUser = null;
  authorizations = [];
  authorizationByUser = {};
  application = new ApplicationResult();
  scopes = [];
  currentPage = 1;
  perPage = 15;
  periods = {
    FROM_DATE: this.$t("dataTypeAuthorizations.from-date"),
    TO_DATE: this.$t("dataTypeAuthorizations.to-date"),
    FROM_DATE_TO_DATE: this.$t("dataTypeAuthorizations.from-date-to-date"),
    ALWAYS: this.$t("dataTypeAuthorizations.always"),
  };

  created() {
    this.init();
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
    ];
  }
  // fillAuthorizationtTree(tree, auth){
  //   tree = tree ||{};
  //   for (const scope in auth.authorizedScopes) {
  //     var nodes = auth.authorizedScopes[scope].split('.')
  //     while(node.length){
  //       var node = nodes.shift();
  //       var nodeScope = tree[node];
  //     }
  //   }
  // }

  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName);
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
      if (this.authorizations && this.authorizations.length !== 0) {
        this.scopes = Object.keys(this.authorizations[0].authorizations);
      }
    } catch (error) {
      this.alertService.toastServerError
      this.authorizationByUser = this.authorizations.reduce((acc, auth) => {
        var user = auth.user;
        var userAuth = acc[user] || [];
        userAuth.push(auth);
        acc[user] = userAuth;
        return acc;
      }, {})(error);
    }
  }

  addAuthorization() {
    this.$router.push(
      `/applications/${this.applicationName}/dataTypes/${this.dataTypeId}/authorizations/new`
    );
  }

  async revoke(id) {
    try {
      await this.authorizationService.revokeAuthorization(
        this.applicationName,
        this.dataTypeId,
        id
      );
      this.alertService.toastSuccess(this.$t("alert.revoke-authorization"));
      this.authorizations.splice(
        this.authorizations.findIndex((a) => a.id === id),
        1
      );
    } catch (error) {
      this.alertService.toastServerError(error);
    }
  }

  getPeriod(authorization) {
    if (!authorization.fromDay && !authorization.toDay) {
      return this.periods.ALWAYS;
    } else if (authorization.fromDay && !authorization.toDay) {
      return (
        this.periods.FROM_DATE +
        ` ${authorization.fromDay[2]}/${authorization.fromDay[1]}/${authorization.fromDay[0]}`
      );
    } else if (!authorization.fromDay && authorization.toDay) {
      return (
        this.periods.TO_DATE +
        ` ${authorization.toDay[2]}/${authorization.toDay[1]}/${authorization.toDay[0]}`
      );
    } else {
      return `${authorization.fromDay[2]}/${authorization.fromDay[1]}/${authorization.fromDay[0]} - ${authorization.toDay[2]}/${authorization.toDay[1]}/${authorization.toDay[0]}`;
    }
  }
}
</script>
