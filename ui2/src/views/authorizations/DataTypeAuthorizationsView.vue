<template>
  <PageView class="with-submenu">
    <SubMenu :root="application.localName || application.title" :paths="subMenuPaths" />
    <h1 class="title main-title">
      {{
        $t("titles.data-type-authorizations", {
          dataType: application.dataTypeName || dataTypeId,
        })
      }}
    </h1>
    <div class="buttons">
      <b-button type="is-primary" @click="addAuthorization" icon-left="plus">
        {{ $t("dataTypeAuthorizations.add-auhtorization") }}
      </b-button>
    </div>

    <b-table
      :data="authorizations"
      :striped="true"
      :isFocusable="true"
      :isHoverable="true"
      :sticky-header="true"
      :paginated="true"
      :per-page="15"
      height="100%"
    >
      <b-table-column
        b-table-column
        field="user"
        :label="$t('dataTypeAuthorizations.user')"
        sortable
        v-slot="props"
      >
        {{ props.row.user }}
      </b-table-column>

      <b-table-column
        b-table-column
        field="dataGroup"
        :label="$t('dataTypeAuthorizations.data-group')"
        sortable
        v-slot="props"
      >
        {{ props.row.dataGroup }}
      </b-table-column>
      <b-table-column
        b-table-column
        field="dataGroup"
        :label="$t('dataTypeAuthorizations.period')"
        sortable
        v-slot="props"
      >
        {{ getPeriod(props.row) }}
      </b-table-column>
      <b-table-column
        v-for="scope in scopes"
        :key="scope"
        b-table-column
        :label="scope"
        sortable
        v-slot="props"
      >
        {{ props.row.authorizedScopes[scope] }}
      </b-table-column>
      <b-table-column b-table-column :label="$t('dataTypeAuthorizations.actions')" v-slot="props">
        <b-button
          type="is-danger"
          size="is-small"
          @click="revoke(props.row.id)"
          icon-left="trash-alt"
        >
          {{ $t("dataTypeAuthorizations.revoke") }}
        </b-button>
      </b-table-column>
    </b-table>
  </PageView>
</template>

<script>
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AuthorizationService } from "@/services/rest/AuthorizationService";
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import { InternationalisationService } from "@/services/InternationalisationService";

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

  authorizations = [];
  application = new ApplicationResult();
  scopes = [];
  periods = {
    FROM_DATE: this.$t("dataTypeAuthorizations.from-date"),
    TO_DATE: this.$t("dataTypeAuthorizations.to-date"),
    FROM_DATE_TO_DATE: this.$t("dataTypeAuthorizations.from-date-to-date"),
    ALWAYS: this.$t("dataTypeAuthorizations.always"),
  };

  localeDatatypeName(datatype) {
    return (
      this.application?.dataTypes?.[datatype]?.internationalizationName?.[this.$i18n.locale] ??
      datatype.name
    );
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
        localName: this.internationalisationService.localeApplicationName(this.application),
        dataTypeName: this.internationalisationService.localeDatatypeName(this.application.dataTypes[this.dataTypeId]),
      };
      this.authorizations = await this.authorizationService.getDataAuthorizations(
        this.applicationName,
        this.dataTypeId
      );
      console.log(this.authorizations);
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
