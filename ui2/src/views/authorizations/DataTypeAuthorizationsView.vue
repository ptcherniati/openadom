<template>
  <PageView class="with-submenu">
    <SubMenu :paths="subMenuPaths" :root="application.localName || application.title" />
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
          <div class="column is-10">
            <p>{{ $t("dataTypeAuthorizations.users") }}</p>
            <b-select v-model="selectedUser" placeholder="Select a name">
              <option v-for="(option, key) in authorizationByUser" :key="key" :value="option">
                {{ key }}
              </option>
            </b-select>
          </div>
          <div class="column is-2">
            <b-button icon-left="plus" type="is-primary is-right" @click="addAuthorization">
              {{ $t("dataTypeAuthorizations.add-auhtorization") }}
            </b-button>
          </div>
        </div>
      </div>

      <b-table
        v-if="selectedUser"
        :data="selectedUser"
        :isFocusable="true"
        :isHoverable="true"
        :sticky-header="true"
        :striped="true"
        class="row"
        height="100%"
        style="padding-bottom: 20px"
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
          :label="$t('dataTypeAuthorizations.data-group')"
          b-table-column
          field="dataGroup"
          sortable
        >
          {{ props.row.dataGroup }}
        </b-table-column>
        <b-table-column
          v-slot="props"
          :label="$t('dataTypeAuthorizations.period')"
          b-table-column
          field="dataGroup"
          sortable
        >
          {{ getPeriod(props.row) }}
        </b-table-column>
        <b-table-column
          v-for="scope in scopes"
          :key="scope"
          v-slot="props"
          :label="scope"
          b-table-column
          sortable
        >
          {{ props.row.authorizedScopes[scope] }}
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
        aria-current-label="Current page"
        aria-next-label="Next page"
        aria-page-label="Page"
        aria-previous-label="Previous page"
        order="is-centered"
        range-after="3"
        range-before="3"
        :rounded="true"
        style="padding-bottom: 20px"
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
  @Prop() applicationName;

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
      this.authorizationByUser = this.authorizations.reduce((acc, auth) => {
        var user = auth.user;
        var userAuth = acc[user] || [];
        userAuth.push(auth);
        acc[user] = userAuth;
        return acc;
      }, {});
      if (this.authorizations && this.authorizations.length !== 0) {
        this.scopes = Object.keys(this.authorizations[0].authorizedScopes);
      }
    } catch (error) {
      this.alertService.toastServerError(error);
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
