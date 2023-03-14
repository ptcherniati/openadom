<template>
  <PageView class="with-submenu">
    <SubMenu
        :root="additionalFile.localName"
        :paths="subMenuPaths"
        role="navigation"
        :aria-label="$t('menu.aria-sub-menu')"
    />
    <h1 class="title main-title">
      {{ $t("titles.additionalFile", additionalFile) }}
    </h1>
    <section class="section" v-if="additionalFiles && canShowForm">
      <article class="addNewAdditionalFileForm" v-show="id!='consult'">
        Le formulaire

        <ValidationProvider
            :rules="rules()"
            ref="provider"
            class="column is-12"
            v-slot="{ errors, valid }"

        >
          <b-field
              class="file is-primary column is-12"
              :type="{
                              'is-danger': errors && errors.length > 0,
                              'is-success': valid,
                            }"
              :message="errors"
              :label="'Fichier additionnel'">
            <b-upload required
                      v-model="file"
                      class="file-label"
                      style="margin-top: 30px"
                      data-cy="changeFileButton"
            >
                        <span class="file-cta">
                          <b-icon class="file-icon" icon="upload"></b-icon>
                          <span class="file-label">{{
                              $t("dataTypesRepository.choose-file")
                            }}</span>
                        </span>
              <span v-if="file" class="file-name">
                          {{ file.name }}
                        </span>
            </b-upload>
            <span>{{ errors[0] }}</span>
          </b-field>
        </ValidationProvider>
        <ValidationObserver tag="form" ref="form" v-slot="{ handleSubmit, invalid }">
          <div v-for="(item,key) in description" :key="key">

            <OreInputText
                v-if="!item.checker ||  item.checker.name == 'RegularExpression' ||  item.checker.name == 'GroovyExpression'"
                :label="internationalisationService.getLocaleforPath(application,'additionalFiles.'+additionalFileName+'.format.'+key, key)"
                :checker="item.checker"
                @update:value="fields[key]=$event"
                v-bind:value="fields[key]"
                :vid="key"/>
            <OreInputNumber
                v-else-if="item.checker.name == 'Integer' || item.checker.name == 'Float' "
                :label="internationalisationService.getLocaleforPath(application,'additionalFiles.'+additionalFileName+'.format.'+key, key)"
                :checker="item.checker"
                @update:value="fields[key]=$event"
                v-bind:value="fields[key]"
                :vid="key"/>
            <OreInputDate
                v-else-if="item.checker.name == 'Date' "
                :label="internationalisationService.getLocaleforPath(application,'additionalFiles.'+additionalFileName+'.format.'+key, key)"
                :checker="item.checker"
                @update:value="fields[key]=$event"
                v-bind:value="fields[key]"
                :vid="key"/>
            <OreInputReference
                v-else-if="item.checker.name == 'Reference'  && refValues[key]"
                :label="internationalisationService.getLocaleforPath(application,'additionalFiles.'+additionalFileName+'.format.'+key, key)"
                :checker="item.checker"
                :references="refValues[key].referenceValues"
                @update:value="fields[key]=$event"
                v-bind:value="fields[key]"
                :vid="key"/>
          </div>
          <div v-if=" application && application.dataTypes">
            <div class="row">
              <div class="columns">
                <b-field
                    v-for="(column, indexColumn) of columnsVisible"
                    :key="indexColumn"
                    :field="indexColumn"
                    :label="getColumnTitle(column)"
                    class="column"
                ></b-field>
              </div>
            </div>
            <ul v-for="dataType in application && application.dataTypes?Object.keys(application.dataTypes):[]"
                :key="dataType"
                class="rows"
            >
              <AdditionalFilesAssociation
                  v-if="applications.length && (isAdmin ||grantables[dataType].canShowLine) "
                  :additionalFiles="additionalFiles"
                  :ref-values="refValues" :configuration="configuration" :application-name="applicationName"
                  :applications="applications"
                  :data-type="dataType"
                  :authorizationId="id"
                  :authorizationsToSet="authorizations"
                  :columns-visible="columnsVisible"
                  :data-type-description="dataTypeDescriptions[dataType]"
                  :grantable-infos=grantables[dataType]
                  @modifyAssociates="modifyAssociate"
                  @update:refValues="refValues=$event"
              />
            </ul>

          </div>
          <b-button
              :type="'is-danger'"
              @click="modifyAssociateFile('consult')"
              icon-left="times-circle"
          >
            Annuler
          </b-button>
          <b-button
              :type="invalid?'is-warning':'is-primary'"
              @click="handleSubmit(changeConfiguration)"
              icon-left="edit"
          >
            Soumettre
          </b-button>
        </ValidationObserver>
      </article>
      <article class="addNewAdditionalFileList" v-if="id=='consult'">

        <div class="columns">
          <!--      <div class="column is-5-desktop is-4-tablet">
                  <b-button
                    icon-left="sort-amount-down"
                    :label="$t('applications.trier')"
                    type="is-dark"
                    @click="showSort = !showSort"
                    outlined
                  ></b-button>
                </div>-->
          <div class="column is-5-desktop is-4-tablet">
            <b-button
                icon-left="filter"
                :label="$t('applications.filter')"
                type="is-light"
                @click="showFilter = !showFilter"
                outlined
                inverted
            ></b-button>
          </div>
          <!--      <div class="column is-2-desktop is-4-tablet">
                  <b-button icon-left="redo" type="is-danger" @click="reInit" outlined
                    >{{ $t("dataTypesManagement.réinitialiser") }}
                    {{ $t("dataTypesManagement.all") }}</b-button
                  >
                </div>-->
        </div>
        <div v-if="showFilter" class="notification" role="search">
          <h2>{{ $t("applications.filter") }}</h2>
          <div class="columns is-multiline">
            <div
                class="column is-2-widescreen is-6-desktop is-12-tablet"
                v-for="(additionalFile, index) in configuration.configuration.additionalFiles[additionalFileName].format"
                :key="additionalFile.id"
                :addtionalFile="additionalFile.id"
            >
              <h1>index:{{ index }}</h1>
              <b-collapse
                  class="card"
                  animation="slide"
                  :open="isOpen === index"
                  @open="isOpen = index"
              >
                <template #trigger="props">
                  <div class="card-header" role="button">
                    <p class="card-header-title" style="text-transform: capitalize">
                      <b-field
                          :label="fieldfilters[index].format.localName"></b-field>
                    </p>
                    <a class="card-header-icon">
                      <b-icon :icon="props.open ? 'chevron-up' : 'chevron-down'"></b-icon>
                    </a>
                  </div>
                </template>
                <div class="card-content" style="padding-bottom: 12px; padding-top: 12px">
                    <b-field v-if="'date' === fieldfilters[index].value.type || 'numeric' === fieldfilters[index].value.type">
                      <CollapsibleInterval
                          :variable-component="fieldfilters[index].value"
                          @setting_interval="fieldfilters[index].value.intervalValues = $event.intervalValues;addAdditionalFileFieldSearch(index)"
                      ></CollapsibleInterval>
                    </b-field>
                    <b-input
                        v-model="fieldfilters[index].value.filter"
                        icon-right="search"
                        :placeholder="$t('dataTypeAuthorizations.search')"
                        type="search"
                        @blur="addAdditionalFileFieldSearch(index)"
                        size="is-small"
                    ></b-input>
                </div>
                <b-field>
                  <b-switch
                      v-model="fieldfilters[index].value.isRegex"
                      passive-type="is-dark"
                      type="is-primary"
                      :true-value="$t('dataTypesManagement.accepted')"
                      :false-value="$t('dataTypesManagement.refuse')"
                  >{{ $t("ponctuation.regEx") }} {{ fieldfilters[index].value.isRegex }}
                  </b-switch
                  >
                </b-field>
                <b-field>{{additionalFile}}</b-field>

              </b-collapse>
            </div>
          </div>

        </div>
        <table
            class="table is-hoverable is-striped is-fullwidth"
            style="text-align: center; vertical-align: center">
          <caption v-if="id=='consult'">
            <b-button icon-left="plus"
                      size="is-small"
                      type="is-primary is-light"
                      @click="modifyAssociateFile('new')">
              Ajouter un fichier
            </b-button>
          </caption>
          <thead>
          <tr
              style="text-align: center; vertical-align: center">
            <th></th>
            <th>Nom</th>
            <th>Taille</th>
            <th>Commentaire</th>
            <th> Créé le</th>
            <th> Par</th>
            <th> Modifié le</th>
            <th>Par</th>
            <th>Associé</th>
            <th>Action</th>
          </tr>
          </thead>
          <tbody v-for="(file) in additionalFiles" :key="file.id">
          <tr>
            <td>
              <b-button :icon-left="showFileInfos==file.id?'caret-down':'caret-right'"
                        @click="toggleFileInfos(file.id)"/>
            </td>
            <td>
              {{ file.fileName }}
            </td>
            <td>
              {{ file.size / 1000 }} ko
            </td>
            <td>
              {{ file.comment }}
            </td>
            <td>
              {{ dateParser(dateParser(file.createDate).toDate()).format("DD/MM/YYYY") }}
            </td>
            <td>
              {{ file.creationUser }}
            </td>
            <td>
              {{ dateParser(dateParser(file.updateDate).toDate()).format("DD/MM/YYYY") }}
            </td>
            <td>
              {{ file.updateUser }}
            </td>
            <td>
              {{ file.associates.length ? 'Oui' : 'Non' }}
            </td>
            <td>
              <b-button icon-left="times-circle"
                        size="is-small"
                        type="is-danger is-light"
              />
              <b-button icon-left="pen-square"
                        size="is-small"
                        type="is-primary is-light"
                        @click="modifyAssociateFile(file.id)"/>
              <b-button icon-left="upload"
                        size="is-small"
                        type="is-primary is-light"
                        @click="download(file)"/>
            </td>
          </tr>
          <tr v-if="showFileInfos==file.id">
            <td colspan="9">
              <table
                  class="table is-hoverable is-striped is-fullwidth"
                  style="text-align: center; vertical-align: center">
                <thead>
                <tr
                    style="text-align: center; vertical-align: center">
                  <th v-for="(field, id) in description" :key="id" :length="field">{{ id }}</th>
                </tr>
                </thead>
                <tbody>
                <tr>
                  <td v-for="(field, id) in description" :key="id" :length="field">{{ file.fileInfos[id] }}</td>
                </tr>
                </tbody>
              </table>
            </td>
          </tr>
          </tbody>
        </table>
      </article>
    </section>
    <section v-else-if="additionalFiles">
      NO RIGHTS
    </section>
    <section v-else>
      LOADING....
    </section>
  </PageView>
</template>

<script>
import SubMenu, {SubMenuPath} from "@/components/common/SubMenu.vue";
import OreInputText from "@/components/common/provider/OreInputText";
import OreInputNumber from "@/components/common/provider/OreInputNumber";
import OreInputDate from "@/components/common/provider/OreInputDate";
import OreInputReference from "@/components/common/provider/OreInputReference";
import AdditionalFilesAssociation from "@/components/common/provider/AdditionalFilesAssociation";
import {extend, ValidationObserver, ValidationProvider} from "vee-validate";
import {AlertService} from "@/services/AlertService";
import {ApplicationService} from "@/services/rest/ApplicationService";
import {AdditionalFileService} from "@/services/rest/AdditionalFileService";
import {AuthorizationService} from "@/services/rest/AuthorizationService";
import {Component, Prop, Vue, Watch} from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import {InternationalisationService} from "@/services/InternationalisationService";
import {ReferenceService} from "@/services/rest/ReferenceService";
import {Authorization} from "@/model/authorization/Authorization";
import moment from "moment";
import {AdditionalFilesInfos} from "@/model/additionalFiles/AdditionalFilesInfos";
import {AdditionalFileInfos} from "@/model/additionalFiles/AdditionalFileInfos";
import {FieldFilters} from "@/model/additionalFiles/FieldFilters";
import CollapsibleInterval from "@/components/common/CollapsibleInterval";
import {VariableComponentFilters} from "@/model/application/VariableComponentFilters";
import {VariableComponentKey} from "@/model/application/VariableComponentKey";
import {IntervalValues} from "@/model/application/IntervalValues";


@Component({
  components: {
    PageView, SubMenu, ValidationProvider, ValidationObserver,
    OreInputText, OreInputNumber, OreInputDate, OreInputReference,
    SubMenuPath, AdditionalFilesAssociation, AuthorizationService, CollapsibleInterval
  },
})
export default class AdditionalFileInfosView extends Vue {
  @Prop() applicationName;
  @Prop() additionalFileName;
  @Prop() id;
  application = null;
  additionalFile = {localName: 'loading...'};
  additionalFiles = null;
  dateParser = moment
  subMenuPaths = [];
  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  additionalFileService = AdditionalFileService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  authorizationService = AuthorizationService.INSTANCE
  description = {}
  fields = {}
  file = null
  referenceService = ReferenceService.INSTANCE;
  refValues = {};
  authorizations = [];
  configuration = null;
  references = {}
  columnsVisible = {
    label: {
      title: "Label",
      display: true,
      internationalizationName: {fr: "Domaine", en: "Domain"},
    },
    associate: {
      title: "Association",
      display: true,
      internationalizationName: {fr: "Associer", en: "Associate"},
    },
  }
  dataTypeDescriptions = null;
  associates = {};
  selectedFile = null;
  grantables = {}
  showFileInfos = {}

  isOpen =false;
  format = null;
  fieldfilters = {}
  openPanel = false;
  showFilter = false;

  @Watch("id")
  onIdChanged(id) {
    this.initForm(id)
  }

  async changeConfiguration() {
    try {
      let authorizationsToSend = [];
      for (const datatype in this.associates) {
        let authorizationToSend = {
          ...this.associates[datatype],
          dataType: datatype,
          applicationNameOrId: this.applicationName,
        };
        for (const scope in authorizationToSend.authorizations) {
          authorizationToSend.authorizations[scope] = authorizationToSend.authorizations[scope].map(
              (auth) => {
                var returnedAuth = new Authorization(auth);
                returnedAuth.intervalDates = {
                  fromDay: returnedAuth.fromDay,
                  toDay: returnedAuth.toDay,
                };
                returnedAuth.dataGroups = returnedAuth.dataGroups.map((dg) => dg.id || dg);
                delete returnedAuth.from
                delete returnedAuth.to
                return returnedAuth;
              }
          );
        }
        delete authorizationToSend.scopes
        delete authorizationToSend.users
        authorizationsToSend.push(authorizationToSend)
      }

      let fields = {...this.fields}
      delete fields.files;

      await this.additionalFileService.saveAdditionalFile(
          this.id == 'new' ? '' : this.id,
          this.additionalFileName,
          this.applicationName,
          this.additionalFileName,
          this.file,
          fields,
          authorizationsToSend);
      this.alertService.toastSuccess('fichier enregistré')
      this.initAdditionalFiles()
      this.$router.push(`/applications/${this.applicationName}/additionalFiles/${this.additionalFileName}/consult`);
    } catch (error) {
      this.alertService.toastServerError(error);
    }

  }

  get canShowForm() {

    return this.isAdmin || (this.grantables && Object.values(this.grantables).find(grantable => grantable.canShowLine))
  }

  get userAuthorizations() {
    return JSON.parse(localStorage.getItem('authenticatedUser'))?.authorizations || [];
  }

  get isAdmin() {
    return this.userAuthorizations.find(auth => new RegExp(auth).test(this.applicationName));
  }

  rules() {
    extend('required', () => {
      return () => this.fields.files && this.fields.files.length
    })
    return 'required'
  };

  applications = [];

  authReferences = {};

  async created() {
    this.subMenuPaths = [
      new SubMenuPath(
          this.$t("additionalFilesmanagement.additionalFilesManagement").toLowerCase(),
          () => this.$router.push(`/applications/${this.applicationName}/additionalFiles`),
          () => this.$router.push("/applications")
      ),
      new SubMenuPath(
          this.$t("additionalFilesmanagement.additionalFilesManagementFor", {additionalFileName: this.additionalFileName}).toLowerCase(),
          () => {
          },
          () => this.$router.push(`/applications/${this.applicationName}/additionalFiles`)
      ),
    ];
    await this.init();
  }

  canShowLine(...args) {
    return !!args.find(arg => {
      return arg && Object.keys(arg).length;
    })
  }

  async init() {
    try {
      this.applications = await this.applicationService.getApplications();
      this.application = await this.applicationService.getApplication(this.applicationName);
      this.dataTypeDescriptions = this.internationalisationService.localeDatatypeName(this.application)
      for (const dataTypeDescription in this.dataTypeDescriptions) {
        this.authorizations[dataTypeDescription] = {}
        const grantableInfos = await this.authorizationService.getAuthorizationGrantableInfos(
            this.applicationName,
            dataTypeDescription
        );
        grantableInfos.canShowLine = this.canShowLine(
            grantableInfos.authorizationsForUser.authorizationByPath.admin,
            grantableInfos.authorizationsForUser.authorizationByPath.publication,
            grantableInfos.authorizationsForUser.authorizationByPath.depot,
        );
        this.grantables[dataTypeDescription] = grantableInfos;
      }
      this.configuration = this.applications.find(app => app.name == this.applicationName)
      if (this.configuration.configuration.additionalFiles) {
        const additionalFileInfos = {}
        additionalFileInfos[this.additionalFileName] = new AdditionalFileInfos();
        this.format = new AdditionalFilesInfos(null, null, null, additionalFileInfos)
        for (const formatName in this.configuration.configuration.additionalFiles[this.additionalFileName].format) {
          const format = this.configuration.configuration.additionalFiles[this.additionalFileName].format[formatName]
          this.fieldfilters[formatName] = {}
          this.fieldfilters[formatName].format = format
          this.fieldfilters[formatName].format.localName = this.internationalisationService.getLocaleforPath(this.application, 'additionalFiles.' + this.additionalFileName + '.format.' + formatName);
          this.fieldfilters[formatName].value = new FieldFilters();
          this.fieldfilters[formatName].value.key = formatName;
          if (format.checker){
            if (format.checker.name == 'Date'){
              this.fieldfilters[formatName].value.type = 'date';
              this.fieldfilters[formatName].value.format = format.checker.params.pattern
            } else if (format.checker.name == 'Integer' || format.checker.name == 'Float'){
              this.fieldfilters[formatName].value.type = 'numeric';
              this.fieldfilters[formatName].value.format=format.checker.name == 'Integer'?'integer':'float'
            }else if (format.checker.name == 'Reference' ){
              this.fieldfilters[formatName].value.type = 'reference';
            }else if (format.checker.name == 'GroovyExpression' ){
              this.fieldfilters[formatName].value.type = 'groovy';
            }else if (format.checker.name == 'RegularExpression' ){
              this.fieldfilters[formatName].value.type = 'regexp';
              this.fieldfilters[formatName].value.format = format.checker.params.pattern
            }
          }
        }
      }
      let localRefName = this.internationalisationService.additionalFilesNames(this.application);
      this.additionalFile = this.convertToNode(localRefName)
      this.additionalFile.fields.forEach(field => this.fields[field] = '')
      this.initAdditionalFiles();
    } catch (error) {
      this.alertService.toastServerError();
    }
  }

  async initAdditionalFiles() {
    let additionalFiles = await this.additionalFileService.getAdditionalFiles(this.applicationName, this.additionalFileName)
    this.additionalFiles = additionalFiles.additionalBinaryFiles
    this.description = additionalFiles.description.format
    for (const name in this.description) {
      let description = this.description[name]
      let refType = description?.checker?.params?.refType
      if (refType) {
        let references = await this.getOrLoadReferences(refType);
        this.refValues[name] = references;
      }
    }
    if (this.id != 'new' && this.id != 'consult') {
      this.initForm(this.id)
    }
  }

  getColumnTitle(column) {
    if (column.display) {
      return (
          (column.internationalizationName && column.internationalizationName[this.$i18n.locale]) ||
          column.title
      );
    }
  }


  async getOrLoadReferences(reference) {
    if (this.references[reference]) {
      return this.references[reference];
    }
    let ref = await this.referenceService.getReferenceValues(this.applicationName, reference);
    this.references[reference] = ref;
    // eslint-disable-next-line no-self-assign
    this.references = this.references;
    return ref;
  }

  convertToNode(additionalFiles) {
    let additionalFile = additionalFiles[this.additionalFileName]
    let af = {
      children: [],
      fields: additionalFile.fields,
      id: this.additionalFileName,
      label: additionalFile.refNameLocal || additionalFile.name,
      localName: additionalFile.refNameLocal || additionalFile.name,
      name: additionalFile.name,
      localFields: additionalFile.localFields
    }
    return af
  }

  modifyAssociate(event) {
    this.associates[event.dataType] = event.associates
  }

  toggleFileInfos(id) {
    let show = this.showFileInfos == id ? null : id
    this.showFileInfos = show
    this.$forceUpdate()
  }

  modifyAssociateFile(id) {
    this.$router.push(`/applications/${this.applicationName}/additionalFiles/${this.additionalFileName}/${id}`);

  }

  initForm(id) {
    let file = this.additionalFiles.find(file => file.id == id)
    let fields = this.fields
    this.file = null;
    for (const fileKey in this.fields) {
      fields[fileKey] = file ? file.fileInfos[fileKey] : ''
    }
    this.fields = fields
    this.$refs.form.$forceUpdate()
    this.authorizations = file ? file.associates : []
  }

  async download(file) {
    let param = new AdditionalFilesInfos(
        [file.id]
    );
    let data = await this.additionalFileService.getAdditionalFileZip(
        this.applicationName, param);
    let blob = new Blob([data], {
      type: "application/zip, application/octet-stream"
    });
    let objectUrl = URL.createObjectURL(blob);
    let link = document.createElement('a');
    link.href = objectUrl;
    link.download = file.fileName + ".zip";
    link.click();
    window.URL.revokeObjectURL(link.href);
    return false;
  }
  addAdditionalFileFieldSearch( index) {
    const value = this.fieldfilters[index].value;
    new FieldFilters(index, value.field, value.type, value.format, )
    let isRegExp = this.params.variableComponentFilters.isRegex;
    let value = this.search[key];
    this.params.variableComponentFilters = this.params.variableComponentFilters.filter(
        (c) =>
            c.variableComponentKey.variable !== variable ||
            c.variableComponentKey.component !== component
    );
    let search = null;
    if (value && value.length > 0) {
      search = new VariableComponentFilters({
        variableComponentKey: new VariableComponentKey({
          variable: variable,
          component: component,
        }),
        filter: value,
        type: type,
        format: format,
        isRegExp: isRegExp,
      });
    }
    if (field.intervalValues) {
      search = new VariableComponentFilters({
        variableComponentKey: new VariableComponentKey({
          variable: variable,
          component: component,
        }),
        type: type,
        format: format,
        isRegExp: isRegExp,
        intervalValues: field.intervalValues,
        ...(search ? new IntervalValues(search) : {}),
      });
    }
    if (search) {
      this.variableSearch.push(search);
    }
    this.initDatatype();
  }
}
</script>

<style lang="scss" scoped>
.table th:not([align]) {
  text-align: center;
}

.authorizationTable {
  margin-left: 10px;
  padding: 0 0 0 5px;

  button {
    opacity: 0.75;
  }

  .dropdown-menu .dropdown-content .dropDownMenu button {
    opacity: 0.5;
  }

  dgSelected {
    color: #007f7f;
  }
}

a {
  color: $dark;
  font-weight: bold;
  text-decoration: underline;
}

a:hover {
  color: $primary;
  text-decoration: none;
}

p {
  font-weight: bold;
}

::marker {
  color: transparent;
}

.column {
  padding: 6px;
}

.show-check-details {
  margin-left: 0.6em;
}

.show-detail-for-selected {
  height: 60px;
}
</style>