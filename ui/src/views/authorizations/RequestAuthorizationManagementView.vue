<template>
  <PageView class="with-submenu">
    <SubMenu
      :aria-label="$t('menu.aria-sub-menu')"
      :paths="subMenuPaths"
      :root="application.localName || application.title"
      role="navigation"
    />
    <h1 class="title main-title">
      {{
        $t("titles.data-type-authorizations", {
          dataType: application.localDatatypeName || dataTypeId,
        })
      }}
    </h1>
    <div>
      <b-select :placeholder="filterStates[filterState].label" v-model="filterState">
        <option v-for="(option, id) in filterStates" :key="id" :value="id">
          {{ option.label }}
        </option>
      </b-select>
      <div v-for="(rightsRequest, i) in rightsRequests.rightsRequests" :key="i">
        <CollapsibleTree
          v-if="isVisibleRequest(rightsRequest.setted)"
          :id="rightsRequest.id"
          :application-title="$t('titles.references-page')"
          :buttons="buttons"
          :level="0"
          :line-count="12"
          :on-click-label-cb="() => ''"
          :option="rightsRequest"
          :repository-redirect="(label) => manageRequest(label)"
          class="liste"
        >
          <template v-slot:secondaryMenu> &nbsp; </template>
          <template v-slot:upload> &nbsp; </template>
          <template v-slot:label="{ option }">
            <b-icon
              :icon="option.setted ? 'check' : 'clock'"
              :type="option.setted ? 'is-primary' : 'is-light'"
              class="column is-one-fifth"
            >
              {{
                option.setted
                  ? $t("dataTypeAuthorizations.scopes.close")
                  : $t("dataTypeAuthorizations.scopes.open")
              }}
            </b-icon>
            <div class="column is-four-fifth">{{ option.comment || "---" }}</div>
          </template>
          <template v-slot:synthesisDetail="{ option }">
            <div class="column is-one-fifth">
              {{ (option.creationDate && getDate(option.creationDate)) || "---" }}
            </div>
            <div class="column is-one-fifth">
              {{ users.find((user) => user.id == option.user).label || "---" }}
            </div>
          </template>
          <template v-slot:default="{ option, displayChildren }">
            <div v-if="displayChildren" class="rows">
              <div v-for="(value, name) in option.rightsRequestForm" :key="name" class="row">
                <div class="columns">
                  <div class="column is-primary">
                    {{
                      internationalisationService.getLocaleforPath(
                        application,
                        "rightsRequest.format." + name,
                        name
                      )
                    }}
                  </div>
                  <div class="column">{{ value }}</div>
                </div>
              </div>
            </div>
          </template>
        </CollapsibleTree>
      </div>
    </div>
  </PageView>
</template>

<script>
import moment from "moment";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { RequestRightsService } from "@/services/rest/RequestRightsService";
import { InternationalisationService } from "@/services/InternationalisationService";
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import { Button } from "@/model/Button";
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";

@Component({
  components: { PageView, SubMenu, CollapsibleTree },
})
export default class RequestAuthorizationManagementView extends Vue {
  @Prop() dataTypeId;
  @Prop() applicationName;
  toList;
  internationalisationService = InternationalisationService.INSTANCE;
  alertService = AlertService.INSTANCE;

  requestRightsService = RequestRightsService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  application = new ApplicationResult();
  // pagination
  offset = 0;
  currentPage = 1;
  perPage = 10;
  isSelectedName = "";
  isSelectedAuthorization = "";
  isCardModalActive = false;
  isCardModalActive2 = false;
  localizations = {};
  rightsRequests = {};
  canManageRights = false;
  users = [];
  buttons = [
    new Button(
      this.canManageRights
        ? this.$t("dataTypeAuthorizations.grantRequests")
        : this.$t("dataTypeAuthorizations.modifyRequests"),
      "eye",
      (label) => this.manageRequest(label),
      "is-dark"
    ),
  ];
  filterState = 0;
  filterStates = [
    {
      type: "open",
      label: this.$t("dataTypeAuthorizations.filterScope.open"),
    },
    {
      type: "close",
      label: this.$t("dataTypeAuthorizations.filterScope.close"),
    },
    {
      type: "all",
      label: this.$t("dataTypeAuthorizations.filterScope.all"),
    },
  ];

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
          this.$router.push(`/applications/${this.applicationName}/authorizations`);
        },
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`)
      ),
    ];
  }

  getDate(value) {
    return moment(value).format("DD/MM/YYYY");
  }

  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName, [
        "CONFIGURATION",
        "DATATYPE",
        "RIGHTSREQUEST",
      ]);
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
      };
      this.canManageRights =
        this.application.isAdministrator ||
        Object.values(this.application.authorizationsDatatypesRights || []).some(
          (rights) => rights.ADMIN
        );
      let rightsRequests = await this.requestRightsService.getRightsRequests(this.applicationName);
      let users1 = rightsRequests.users || [];
      users1.shift();
      users1 = users1.filter((user) => {
        return rightsRequests.rightsRequests.some((rr) => rr.user == user.id);
      });
      for (const request of rightsRequests.rightsRequests) {
        request.children = [{}];
        request.label = request.id;
      }
      this.rightsRequests = rightsRequests;
      this.users = users1;
    } catch (error) {
      this.alertService.toastServerError;
    }
  }

  isVisibleRequest(setted) {
    if (this.filterState == 0) {
      return !setted;
    } else if (this.filterState == 1) {
      return setted;
    } else {
      return true;
    }
  }

  manageRequest(id) {
    this.$router.push(`/applications/${this.applicationName}/authorizationsRequest/${id}`);
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
