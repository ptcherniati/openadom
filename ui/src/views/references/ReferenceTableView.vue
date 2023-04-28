<template>
  <PageView class="with-submenu">
    <SubMenu
      :aria-label="$t('menu.aria-sub-menu')"
      :paths="subMenuPaths"
      :root="application.localName"
      role="navigation"
    />
    <h1 class="title main-title">
      {{ $t("titles.references-data", { refName: application.localRefName }) }}
    </h1>
<!--    <b-modal v-model="currentReferenceDetail.active" custom-class="referenceDetails">
      <div class="card">
        <header class="card-header is-align-content-center">
          <p class="card-header-title" field="name" style="font-size: 1.5rem; color: #007f7f">
            {{ currentReferenceDetail.reference }}
          </p>
        </header>
        <div class="card-content">
          <div class="content is-align-content-center">
            <b-table :data="currentReferenceDetail.data">
              <b-table-column
                v-for="column in currentReferenceDetail.columns"
                :key="column.id"
                v-slot="props"
              >
                <span
                  v-for="refParent in currentReferenceDetail.refParent"
                  :key="refParent.valueRefParent"
                >
                  <a
                    v-if="refParent.valueRefParent === props.row[column.field]"
                    @click="
                      getCheckerReferenceValues(
                        refParent.valueRefParent,
                        refParent.nameRefParent,
                        refParent.idRefParent[refParent.nameRefParent]
                      )
                    "
                  >
                    {{ props.row[column.field] }}
                  </a>
                  <p
                    v-else-if="
                      !props.row[column.field].length && props.row[column.field].length !== 0
                    "
                  >
                    <b-button
                      v-if="showBtnTablDynamicColumn(props.row[column.field])"
                      icon-left="eye"
                      rounded
                      size="is-small"
                      style="height: inherit"
                      type="is-dark"
                      @click="showModal(column.field, props.row[column.field])"
                    >
                    </b-button>
                  </p>
                  <p v-else>{{ props.row[column.field] }}</p>
                </span>
              </b-table-column>
            </b-table>
          </div>
        </div>
      </div>
    </b-modal>
    -->

    <div v-if="reference && columns">
      <b-table
        :current-page="currentPage"
        :data="tableValues"
        :is-focusable="true"
        :is-hoverable="true"
        :loading="isLoading"
        :per-page="params.limit"
        :sticky-header="true"
        :striped="true"
        height="72.5vh"
        paginated
        style="padding-bottom: 20px; position: relative; z-index: 1"
      >
        <template #pagination>
          <b-pagination
            v-model="currentPage"
            :aria-current-label="$t('menu.aria-curent-page')"
            :aria-label="$t('menu.aria-pagination')"
            :aria-next-label="$t('menu.aria-next-page')"
            :aria-previous-label="$t('menu.aria-previous-page')"
            :current-page.sync="currentPage"
            :per-page="params.limit"
            :rounded="true"
            :total="totalRows"
            order="is-centered"
            range-after="3"
            range-before="3"
            role="navigation"
            @change="changePage"
          />
        </template>
        <b-table-column
          v-for="column in columns"
          :key="column.id"
          v-slot="props"
          :field="column.id"
          :label="column.title"
          :sticky="column.key"
          sortable
        >
          <ReferencesDynamicLink
            v-if="info(column.id)"
            :info="info(column.id)"
            :info-values="props.row[column.id]"
            :application="application"
            :reference-type="column.reference"
            :loaded-references-by-key="{}"
            :column-id="column.id"
            :column-title="column.title"
          ></ReferencesDynamicLink>
          <ReferencesManyLink
            v-else-if="multiplicity(column.id, props.row[column.id])"
            :multiplicity="multiplicity(column.id, props.row[column.id])"
            :info-values="props.row[column.id]"
            :application-name="applicationName"
            :reference-type="column.linkedTo"
            :loaded-references-by-key="{}"
            :column-id="column.id"
            :column-title="column.title"
          ></ReferencesManyLink>
          <ReferencesLink
            v-else-if="column.id !== '#'"
            :application="application"
            :reference-type="column.linkedTo"
            :value="
              info(column.id) || multiplicity(column.id, props.row[column.id])
                ? ''
                : props.row[column.id]
            "
            :loaded-references-by-key="{}"
            :column-title="column.title"
          ></ReferencesLink>
          <b-collapse v-else :open="false">
            <template #trigger>
              <b-button
                :label="'' + (tableValues.indexOf(props.row) + 1 + params.offset)"
                aria-controls="contentIdForA11y1"
                type="is-small"
              />
            </template>
            {{ referenceValues[tableValues.indexOf(props.row)].naturalKey }}
          </b-collapse>
        </b-table-column>
      </b-table>
    </div>
  </PageView>
</template>

<script>
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { ReferenceService } from "@/services/rest/ReferenceService";
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { InternationalisationService } from "@/services/InternationalisationService";
import { DownloadDatasetQuery } from "@/model/application/DownloadDatasetQuery";
import ReferencesLink from "@/components/references/ReferencesLink.vue";
import ReferencesManyLink from "@/components/references/ReferencesManyLink.vue";
import ReferencesDynamicLink from "@/components/references/ReferencesDynamicLink.vue";

@Component({
  components: { PageView, SubMenu, ReferencesLink, ReferencesManyLink, ReferencesDynamicLink },
})
export default class ReferenceTableView extends Vue {
  @Prop() applicationName;
  @Prop() refId;

  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  referenceService = ReferenceService.INSTANCE;
  params = new DownloadDatasetQuery({
    application: null,
    applicationNameOrId: this.applicationName,
    reference: this.refId,
    offset: 0,
    limit: 10,
  });
  totalRows = -1;
  currentPage = 1;
  isLoading = false;
  application = new ApplicationResult();
  subMenuPaths = [];
  reference = {};
  columns = [];
  referenceValues = [];
  tableValues = [];

  // show modal and cards
  isCardModalActive = false;
  modalArrayObj = [];
  modalTblObj = [];
  referencesDynamic;
  display = "__display_" + window.localStorage.lang;
  loadedReferences = {};
  currentReferenceDetail = { active: false };

  async changePage(value) {
    this.params.offset = (value - 1) * this.params.limit;
    const references = await this.referenceService.getReferenceValues(
      this.applicationName,
      this.refId,
      {
        _offset_: this.params.offset,
        _limit_: this.params.limit,
      }
    );
    if (references) {
      this.referenceValues = references.referenceValues;
    }
    if (this.referenceValues) {
      this.tableValues = Object.values(this.referenceValues).map((refValue) => refValue.values);
    }
  }

  async showModal(columName, tablDynamicColumn) {
    this.isCardModalActive = true;
    this.currentReferenceDetail.active = false;
    this.modalArrayObj = Object.entries(tablDynamicColumn)
      .filter((a) => a[1])
      .map(function (a) {
        let obj = {};
        obj[a[0]] = a[1];
        return obj;
      });
    if (this.referencesDynamic) {
      for (let i = 0; i < this.referencesDynamic.referenceValues.length; i++) {
        let hierarchicalKey = this.referencesDynamic.referenceValues[i].hierarchicalKey;
        for (let j = 0; j < this.modalArrayObj.length; j++) {
          if (this.modalArrayObj[j][hierarchicalKey]) {
            let column = this.referencesDynamic.referenceValues[i].values[this.display]
              ? this.referencesDynamic.referenceValues[i].values[this.display]
              : hierarchicalKey;
            let value = this.modalArrayObj[j][hierarchicalKey];
            this.modalArrayObj[j] = { ...this.modalArrayObj[j], column: column, value: value };
          }
        }
        for (let j = 0; j < tablDynamicColumn.length; j++) {
          if (tablDynamicColumn[j] === hierarchicalKey) {
            let column = this.referencesDynamic.referenceValues[i].values[this.display]
              ? this.referencesDynamic.referenceValues[i].values[this.display]
              : columName;
            this.modalArrayObj[j] = {
              ...this.modalArrayObj[j],
              column: column,
              value: hierarchicalKey,
            };
            /*this.paramsForMany = { ...this.paramsForMany, row_id_:hierarchicalKey }
            console.log(this.paramsForMany)
            const reference = await this.referenceService.getReferenceValues(
                this.applicationName,
                columName,
                this.paramsForMany
            );
            console.log(reference)*/
          }
        }
      }
      return this.modalArrayObj;
    }
    return this.modalArrayObj;
  }

  info(refType) {
    let dynamicColumns = Object.entries(this.reference.dynamicColumns).filter((a) => a[1]);
    //console.log(dynamicColumns)
    for (let i = 0; i < dynamicColumns.length; i++) {
      if (dynamicColumns[i][0] === refType) return true;
    }
    return false;
  }

  showBtnTablDynamicColumn(tablDynamicColumn) {
    let showModal = Object.entries(tablDynamicColumn)
      .filter((a) => a[1])
      .map(function (a) {
        let obj = {};
        obj[a[0]] = a[1];
        return obj;
      });
    return showModal.length !== 0;
  }

  multiplicity(column, arrayValues) {
    for (let i = 0; i < this.tableValues.length; i++) {
      let showModal = Object.entries(this.tableValues[i]).filter((a) => a[1]);
      for (let j = 0; j < showModal.length; j++) {
        if (
          showModal[j][0] === column &&
          showModal[j][1] === arrayValues &&
          Array.isArray(showModal[j][1])
        ) {
          return true;
        }
      }
    }
    return false;
  }

  async created() {
    await this.init();
    await this.setInitialVariables();
  }

  async init() {
    this.isLoading = true;
    try {
      this.application = await this.applicationService.getApplication(this.applicationName, [
        "CONFIGURATION",
        "REFERENCETYPE",
      ]);
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
        localRefName: this.internationalisationService.localeReferenceName(
          this.application.references[this.refId],
          this.application
        ),
      };
      const references = await this.referenceService.getReferenceValues(
        this.applicationName,
        this.refId,
        {
          _offset_: this.params.offset,
          _limit_: this.params.limit,
        }
      );
      if (references) {
        this.referenceValues = references.referenceValues;
      }
      for (let i = 0; i < this.application.referenceSynthesis.length; i++) {
        if (this.application.referenceSynthesis[i].referenceType === this.refId) {
          this.totalRows = this.application.referenceSynthesis[i].lineCount;
        }
      }
    } catch (error) {
      this.alertService.toastServerError();
    }
    this.isLoading = false;
  }

  async getCheckerReferenceValues(rowId, refType, checkerId) {
    if (Object.keys(rowId)[0].length > 3) {
      rowId = Object.keys(rowId)[0];
    }
    let refTypeName;
    this.isCardModalActive = false;
    if (
      this.application.internationalization.references[refType]?.internationalizationName[
        window.localStorage.lang
      ]
    ) {
      refTypeName =
        this.application.internationalization.references[refType].internationalizationName[
          window.localStorage.lang
        ];
    } else if (this.application.references[refType]?.label) {
      refTypeName = this.application.references[refType].label;
    } else {
      refTypeName = refType;
    }
    for (let i = 0; i < this.referenceValues.length; i++) {
      if (this.referenceValues[i].naturalKey === rowId) {
        checkerId = this.referenceValues[i].refsLinkedTo[refType][0];
      }
    }
    if (!this.loadedReferences[checkerId]) {
      let refvalues;
      let valueRefParent;
      let nameRefParent;
      let idRefParent;
      if (!refvalues) {
        let params = { _row_id_: [checkerId] };
        if (!refType) {
          params.any = true;
        }
        const reference = await this.referenceService.getReferenceValues(
          this.applicationName,
          refType,
          params
        );
        if (Object.keys(reference.referenceValues[0].refsLinkedTo).length > 0) {
          valueRefParent = reference.referenceValues[0].hierarchicalKey.split(".")[0];
          nameRefParent = reference.referenceValues[0].hierarchicalReference.split(".")[0];
          idRefParent = reference.referenceValues[0].refsLinkedTo;
        }
        refvalues = reference.referenceValues[0].values;
      }
      const data = Object.entries(refvalues)
        .map((entry) => ({ colonne: entry[0], valeur: entry[1] }))
        .reduce((acc, entry) => {
          acc.push(entry);
          return acc;
        }, []);
      const result = {};
      result[checkerId] = {
        data: data,
        columns: [
          {
            field: "colonne",
            label: "Colonne",
          },
          {
            field: "valeur",
            label: "Valeur",
          },
        ],
        active: true,
        reference: refTypeName,
        refParent: [
          {
            valueRefParent: valueRefParent,
            nameRefParent: nameRefParent,
            idRefParent: idRefParent,
          },
        ],
      };
      this.loadedReferences = {
        ...this.loadedReferences,
        ...result,
      };
    }
    let referenceValue = this.loadedReferences[checkerId];
    this.currentReferenceDetail = { ...referenceValue, active: true };
    return referenceValue;
  }

  async setInitialVariables() {
    if (!this.application?.references) {
      return;
    }

    this.reference = Object.values(this.application.references).find(
      (ref) => ref.id === this.refId
    );

    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("referencesManagement.references").toLowerCase(),
        () => this.$router.push(`/applications/${this.applicationName}/references`),
        () => this.$router.push(`/applications`)
      ),
      new SubMenuPath(
        this.reference.label,
        () => this.$router.push(`/applications/${this.applicationName}/references/${this.refId}`),
        () => this.$router.push(`/applications/${this.applicationName}/references`)
      ),
    ];

    if (this.reference && this.reference.columns) {
      this.columns = [
        { id: "#", title: "#id", key: false, linkedTo: null },
        ...Object.values(this.reference.columns).sort((c1, c2) => {
          if (c1.title < c2.title) {
            return -1;
          }
          if (c1.title > c2.title) {
            return 1;
          }
          return 0;
        }),
        ...Object.values(this.reference.dynamicColumns).sort((c1, c2) => {
          if (c1.title < c2.title) {
            return -1;
          }
          if (c1.title > c2.title) {
            return 1;
          }
          return 0;
        }),
      ];
    }
    if (this.referenceValues) {
      this.tableValues = Object.values(this.referenceValues).map((refValue) => refValue.values);
    }
    let dynamicColumns = Object.entries(this.reference.dynamicColumns).filter((a) => a[1]);
    for (let i = 0; i < dynamicColumns.length; i++) {
      this.referencesDynamic = await this.referenceService.getReferenceValues(
        this.applicationName,
        dynamicColumns[i][1].reference,
        {
          _offset_: this.offset,
          _limit_: this.limit,
        }
      );
    }
    let interNameColumn = Object.entries(this.application.internationalization.references).filter(
      (a) => a[1]
    );
    for (let i = 0; i < this.columns.length; i++) {
      for (let j = 0; j < interNameColumn.length; j++) {
        if (interNameColumn[j][0] === this.reference.id) {
          let listInterHeaderColumn = Object.entries(
            interNameColumn[j][1].internationalizedDynamicColumns
          ).filter((a) => a[1]);
          for (let g = 0; g < listInterHeaderColumn.length; g++) {
            if (this.columns[i].id === listInterHeaderColumn[g][0]) {
              let tradNameColumn = Object.entries(listInterHeaderColumn[g][1]).filter((a) => a[1]);
              for (let x = 0; x < tradNameColumn.length; x++) {
                if (tradNameColumn[x][0] === window.localStorage.lang) {
                  this.columns[i].title = tradNameColumn[x][1];
                }
              }
            }
          }
        }
      }
      if (this.application.references[this.columns[i].id]) {
        this.referencesDynamic = await this.referenceService.getReferenceValues(
          this.applicationName,
          this.columns[i].id
        );
      }
    }
  }
}
</script>
<style lang="scss" scoped>
.b-table .table th.is-sortable {
  width: max-content;
  position: sticky;
}
</style>
