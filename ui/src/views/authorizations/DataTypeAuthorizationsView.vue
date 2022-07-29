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
          <div class="column is-offset-10 is-2">
            <b-button icon-left="plus" type="is-primary is-right" @click="addAuthorization">
              {{ $t("dataTypeAuthorizations.add-auhtorization") }}
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
            :current-page.sync="currentPage"
            :per-page="perPage"
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
            @change="changePage"
          />
        </template>
        <b-table-column
          :label="$t('dataTypeAuthorizations.name')"
          b-table-column
          field="name"
          sortable
          :searchable="true"
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
              <b-tooltip position="is-right" :label="$t('dataTypeAuthorizations.showMore')">
                <a
                  class="show-check-details column is-half"
                  type="is-primary "
                  @click="showModal2(user)"
                  style="color: #006464ff; margin-left: 10px"
                >
                  {{ user }}
                </a>
              </b-tooltip>
              <b-modal v-model="isCardModalActive2" v-show="isSelectedName === user">
                <div class="card">
                  <div class="card-header">
                    <div class="title card-header-title">
                      <p field="name">{{ user }}</p>
                    </div>
                  </div>
                  <div class="card-content">
                    <div class="content">
                      <h3>
                        {{ isSelectedName }}
                      </h3>
                    </div>
                  </div>
                </div>
              </b-modal>
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
              <b-tooltip position="is-right" :label="$t('dataTypeAuthorizations.showMore')">
                <a
                  class="show-check-details column is-half"
                  type="is-primary "
                  @click="showModal(props.row.name, authorization)"
                  style="color: #006464ff; margin-left: 10px"
                >
                  {{ authorization }}
                </a>
              </b-tooltip>
              <b-modal
                v-model="isCardModalActive"
                v-show="
                  isSelectedName === props.row.name && isSelectedAuthorization === authorization
                "
              >
                <div class="card">
                  <div class="card-header">
                    <div class="title card-header-title">
                      <p field="name">{{ authorization }}</p>
                    </div>
                  </div>
                  <div class="card-content">
                    <div class="content">
                      <h3>
                        {{ $t("dataTypesManagement.filtered") }} {{ $t("ponctuation.colon") }}
                      </h3>
                      <div
                        v-for="(configuration, idx) in props.row.authorizations[authorization]"
                        v-bind:key="idx"
                        class="listAuthorization"
                      >
                        <div>
                          <p>
                            <span>
                              {{ $t("dataTypeAuthorizations.localization") }}
                              {{ $t("ponctuation.colon") }}
                              <i>{{ configuration.requiredAuthorizations.localization }}</i>
                              {{ $t("ponctuation.semicolon") }}
                            </span>
                          </p>
                          <p>
                            <span v-if="configuration.dataGroups.length === 0">
                              {{ $t("dataTypeAuthorizations.data-group") }}
                              {{ $t("ponctuation.colon") }}
                              <i>{{ $t("dataTypeAuthorizations.all-variable") }}</i>
                              {{ $t("ponctuation.semicolon") }}
                            </span>
                            <span v-else>
                              {{ $t("dataTypeAuthorizations.data-group") }}
                              {{ $t("ponctuation.colon") }} <i>{{ configuration.dataGroups }}</i>
                              {{ $t("ponctuation.semicolon") }}
                            </span>
                          </p>
                          <p>
                            <span>
                              {{ $t("dataTypeAuthorizations.period") }}
                              {{ $t("ponctuation.colon") }} <i>{{ getPeriod(configuration) }}</i>
                              {{ $t("ponctuation.semicolon") }}
                            </span>
                          </p>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </b-modal>
            </div>
          </template>
        </b-table-column>
        <b-table-column v-slot="props" :label="$t('dataTypeAuthorizations.actions')" b-table-column>
          <b-button
            icon-left="times-circle"
            size="is-small"
            type="is-danger is-light"
            @click="revoke(props.row.uuid)"
            style="height: 1.5em; background-color: transparent; font-size: 1.45rem"
          >
          </b-button>
          <b-button
            icon-left="pen-square"
            size="is-small"
            type="is-warning"
            @click="modifyAuthorization(props.row.uuid)"
            outlined
            onmouseover="this.style.color='rgba(255,140,0,0.5)'"
            onmouseout="this.style.color='';"
            style="
              height: 1.5em;
              background-color: transparent;
              font-size: 1.45rem;
              border-color: transparent;
            "
          >
          </b-button>
        </b-table-column>
      </b-table>
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
  toList;

  authorizationService = AuthorizationService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  selectedUser = null;
  authorizations = [];
  authorizationByUser = {};
  application = new ApplicationResult();
  // pagination
  offset = 0;
  currentPage = 1;
  perPage = 10;
  isSelectedName = "";
  isSelectedAuthorization = "";
  isCardModalActive = false;
  isCardModalActive2 = false;
  periods = {
    FROM_DATE: this.$t("dataTypeAuthorizations.from-date"),
    TO_DATE: this.$t("dataTypeAuthorizations.to-date"),
    FROM_DATE_TO_DATE: this.$t("dataTypeAuthorizations.from-date-to-date"),
    ALWAYS: this.$t("dataTypeAuthorizations.always"),
  };

  async changePage(page) {
    this.offset = (page - 1) * this.perPage;
  }

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
      this.alertService.toastServerError;
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

  modifyAuthorization(id) {
    this.$router.push(
      `/applications/${this.applicationName}/dataTypes/${this.dataTypeId}/authorizations/${id}`
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
    window.location.reload();
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

  showModal2(name) {
    this.isSelectedName = name;
    this.isCardModalActive2 = true;
  }

  showModal(name, authorization) {
    this.isSelectedName = name;
    this.isSelectedAuthorization = authorization;
    this.isCardModalActive = true;
  }
}
</script>
<style lang="scss">
td {
  padding: 6px;

  .columns {
    margin: 0;

    .column.is-half {
      padding: 6px;
    }
  }
}

.listAuthorization {
  border: solid #dbdbdb;
  border-width: 0 0 1px;
  margin: 0 10px 0 10px;
  padding: 15px;
}

.listAuthorization:nth-child(odd) {
  background-color: #f5f5f5;
}
</style>
