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
                  v-if="applications.length && grantables[dataType].canShowLine "
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
import {AdditionalFileService} from "@/services/rest/AdditionalFiles";
import {AuthorizationService} from "@/services/rest/AuthorizationService";
import {Component, Prop, Vue, Watch} from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import {InternationalisationService} from "@/services/InternationalisationService";
import {ReferenceService} from "@/services/rest/ReferenceService";
import {Authorization} from "@/model/authorization/Authorization";
import moment from "moment";


@Component({
  components: {
    PageView, SubMenu, ValidationProvider, ValidationObserver,
    OreInputText, OreInputNumber, OreInputDate, OreInputReference,
    SubMenuPath, AdditionalFilesAssociation, AuthorizationService
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
    return this.grantables && Object.values(this.grantables).find(grantable => grantable.canShowLine)
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
      let localRefName = this.internationalisationService.additionalFilesNames(this.application);
      this.additionalFile = this.convertToNode(localRefName)
      this.additionalFile.fields.forEach(field => this.fields[field] = '')
      this.initAdditionalFiles();
    } catch (error) {
      this.alertService.toastServerError();
    }
  }

  async initAdditionalFiles(){
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
    if (this.id!='new' && this.id!='consult'){
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
  modifyAssociateFile(id){
     this.$router.push(`/applications/${this.applicationName}/additionalFiles/${this.additionalFileName}/${id}`);

  }
  initForm(id){
    let file =this.additionalFiles.find(file=>file.id==id)
    let fields = this.fields
    this.file=null;
    for (const fileKey in this.fields) {
      fields[fileKey] = file?file.fileInfos[fileKey]:''
    }
    this.fields=fields
    this.$refs.form.$forceUpdate()
    this.authorizations= file?file.associates:[]
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