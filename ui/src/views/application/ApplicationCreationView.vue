<template>
  <div>
    <PageView>
      <h1 class="title main-title">{{ $t("titles.application-creation") }}</h1>
      <div>
        <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
          <ValidationProvider
            rules="required|validApplicationName|validApplicationNameLength"
            name="applicationsName"
            v-slot="{ errors, valid }"
            vid="applicationsName"
          >
            <b-field
              class="input-field"
              :type="{
                'is-danger': errors && errors.length > 0,
                'is-success': valid,
              }"
              :message="errors[0]"
            >
              <template slot="label">
                {{ $t("applications.name") }}
                <span class="mandatory">
                  {{ $t("validation.obligatoire") }}
                </span>
              </template>
              <b-input
                v-model="applicationConfig.name"
                :placeholder="$t('applications.name-placeholder')"
              >
              </b-input>
            </b-field>
          </ValidationProvider>
          <ValidationProvider
            rules="required"
            name="applicationCreation"
            v-slot="{ errors, valid }"
            vid="applicationCreation"
          >
            <b-field
              class="file is-primary"
              :type="{
                'is-danger': errors && errors.length > 0,
                'is-success': valid,
              }"
            >
              <b-upload v-model="applicationConfig.file" class="file-label" accept=".yaml, .zip"
                        @input="loadingFile"
              >
                <span class="file-cta">
                  <b-icon class="file-icon" icon="upload"></b-icon>
                  <span class="file-label">{{ $t("applications.chose-config") }}</span>
                </span>
                <span class="file-name" v-if="applicationConfig.file">
                  {{ applicationConfig.file.name }}
                </span>
                <span class="file-name" v-if="currentYaml.application">
                  Version : {{ currentYaml.application.version }}
                  Name : {{ currentYaml.application.name }}
                </span>
              </b-upload>
            </b-field>
          </ValidationProvider>
          <div class="columns">
            <b-field class="column" :label="$t('dataTypesRepository.comment')" expanded>
              <b-input v-model="comment" maxlength="200" type="textarea"></b-input>
            </b-field>
          </div>
          <div class="buttons">
            <b-button type="is-light" @click="handleSubmit(testApplication)" icon-left="vial">
              {{ $t("applications.test") }}
            </b-button>
            <b-button type="is-warning" @click="handleSubmit(changeConfiguration)" icon-left="edit">
              {{ $t("applications.change") }}
            </b-button>
            <b-button type="is-primary" @click="handleSubmit(createApplication)" icon-left="plus">
              {{ $t("applications.create") }}
            </b-button>
          </div>
        </ValidationObserver>
        <div v-if="errorsMessages.length">
          <div v-for="msg in errorsMessages" v-bind:key="msg">
            <b-message
              :title="$t('message.app-config-error')"
              type="is-danger"
              has-icon
              :aria-close-label="$t('message.close')"
              class="mt-4"
            >
              <span v-html="msg" />
            </b-message>
          </div>
        </div>
      </div>
    </PageView>
  </div>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { ValidationObserver, ValidationProvider } from "vee-validate";
import { ApplicationConfig } from "@/model/ApplicationConfig";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AlertService } from "@/services/AlertService";
import { ErrorsService } from "@/services/ErrorsService";
import { HttpStatusCodes } from "@/utils/HttpUtils";
import JSYaml from "js-yaml";
var JSZip = require("jszip");


@Component({
  components: { PageView, ValidationObserver, ValidationProvider },
})
export default class ApplicationCreationView extends Vue {
  applicationService = ApplicationService.INSTANCE;
  errorsService = ErrorsService.INSTANCE;
  alertService = AlertService.INSTANCE;

  applicationConfig = new ApplicationConfig();
  errorsMessages = [];
  comment = "";
  currentYaml="";
  completeFile = {};
  JSZip = new JSZip();
  async createApplication() {
    this.errorsMessages = [];
    try {
      await this.applicationService.createApplication(this.applicationConfig, this.comment);
      this.alertService.toastSuccess(this.$t("alert.application-creation-success"));
      this.$router.push("/applications");
    } catch (error) {
      this.checkMessageErrors(error);
    }
  }

  async changeConfiguration() {
    this.errorsMessages = [];
    try {
      await this.applicationService.changeConfiguration(this.applicationConfig, this.comment);
      this.alertService.toastSuccess(this.$t("alert.application-edit-success"));
      this.$router.push("/applications");
    } catch (error) {
      this.checkMessageErrors(error);
    }
  }

  async testApplication() {
    this.errorsMessages = [];
    try {
      let response = await this.applicationService.validateConfiguration(this.applicationConfig);
      if (response.valid === true) {
        this.alertService.toastSuccess(this.$t("alert.application-validate-success"));
      } else {
        this.errorsMessages = this.errorsService.getErrorsMessages(response.validationCheckResults);
      }
    } catch (error) {
      this.checkMessageErrors(error);
    }
  }

  checkMessageErrors(error) {
    if (error.httpResponseCode === HttpStatusCodes.BAD_REQUEST) {
      this.errorsMessages = this.errorsService.getErrorsMessages(
        error.content.validationCheckResults
      );
    } else {
      this.alertService.toastServerError(error);
    }
  }
  loadingFile(inputfile){
    var zip = this.JSZip;
    if(inputfile.type=="application/zip"){
      zip.loadAsync(inputfile);
      zip.loadAsync( inputfile /* = file blob */)
          .then(zipFiles=>this.compile(zipFiles ));
    }else {
      inputfile.text().then(this.readFile)
    }
  }

  compile(zipFiles){
    this.currentYaml = {};
    for (const filePath in zipFiles.files) {
      this.readNode(filePath, zipFiles.files[filePath])
    }
  }
  readNode(filePath, unzippedFile) {
    var name = unzippedFile.name
    if (name.match('.*.yaml')) {
      this.JSZip.file(name).async('blob').then(node => this.addZipNode(node, name));
    }
  }
  readBlob(file, name){
    var split = name.split('/');
    try {
      var yaml = JSYaml.load(file);
      var obj = split
          .reverse()
          .map(s=>s.replace('.yaml', ''))
          .reduce((acc, pathName)=>{
            var obj = {};
            obj[pathName] = acc
            return "configuration.yaml"==name?acc:obj
          }, yaml)
      this.mergeDeep(obj, this.currentYaml)
      this.currentYaml = obj
    }catch (e) {
      console.log(e)
    }
  }
  isObject(item) {
    return (item && typeof item === 'object' && !Array.isArray(item));
  }
  mergeDeep(target, ...sources) {
    if (!sources.length) return target;
    const source = sources.shift();

    if (this.isObject(target) && this.isObject(source)) {
      for (const key in source) {
        if (this.isObject(source[key])) {
          if (!target[key]) Object.assign(target, {[key]: {}});
          this.mergeDeep(target[key], source[key]);
        } else {
          Object.assign(target, {[key]: source[key]});
        }
      }
    }
  }
  addZipNode( blob,  name){
        blob.text().then((file)=>this.readBlob(file, name));
  }
  readFile(file){
    try {
      var yaml = JSYaml.load(file)
      this.currentYaml = yaml
    }    catch (e) {
      console.log( e);
    }
  }
}
</script>