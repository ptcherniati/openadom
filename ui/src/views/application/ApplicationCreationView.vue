<template>
  <div>
    <PageView>
      <h1 class="title main-title">{{ $t("titles.application-creation") }}</h1>
      <div>
        <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
          <div class="columns">
            <ValidationProvider
                v-slot="{ errors, valid }"
                class="column is-4"
                name="applicationCreation"
                rules="required"
                vid="applicationCreation"
            >
              <b-field
                  :type="{
                  'is-danger': errors && errors.length > 0,
                  'is-success': valid,
                }"
                  class="file is-primary"
              >
                <b-upload v-model="applicationConfig.file" accept=".yaml, .zip" class="file-label">
                  <span class="file-cta">
                    <b-icon class="file-icon" icon="upload"></b-icon>
                    <span class="file-label">{{ $t("applications.chose-config") }}</span>
                  </span>
                  <span v-if="applicationConfig.file" class="file-name">
                    {{ applicationConfig.file.name }}
                  </span>
                </b-upload>
                <sup>
                  <b-tooltip :label="$t('applications.help_config')" position="is-right">
                    <a style="color: #006464ff; margin-left: 10px" @click="showHelp"
                    >
                      <b-icon icon="question-circle"></b-icon
                      >
                    </a>
                  </b-tooltip>
                </sup>
              </b-field>
            </ValidationProvider>
            <div class="column is-4" style="margin: 5px">
              <b-button icon-left="vial" type="is-light" @click="handleSubmit(testApplication)">
                {{ $t("applications.test") }}
              </b-button>
            </div>
            <div class="column is-4">
              <b-tag v-if="btnUpdateConfig" size="is-large" style="margin: 5px" type="is-warning">
                {{ $t("applications.app_version") }}{{ applicationConfig.version }}
              </b-tag>
            </div>
          </div>
          <div class="columns">
            <ValidationProvider
                v-if="applicationConfig.name"
                v-slot="{ errors, valid }"
                class="column"
                name="applicationsName"
                vid="applicationsName"
            >
              <b-field
                  :message="errors[0]"
                  :type="{
                  'is-danger': errors && errors.length > 0,
                  'is-success': valid,
                }"
                  class="input-field"
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
          </div>
          <div class="columns">
            <b-field :label="$t('dataTypesRepository.comment')" class="column" expanded>
              <b-input v-model="comment" maxlength="200" type="textarea"></b-input>
            </b-field>
          </div>
          <div class="buttons">
            <b-button
                v-if="btnUpdateConfig"
                icon-left="edit"
                type="is-warning"
                @click="handleSubmit(changeConfiguration)"
            >
              {{ $t("applications.change") }}
            </b-button>
            <b-button
                v-if="applicationConfig.name !== '' && !btnUpdateConfig"
                icon-left="plus"
                type="is-primary"
                @click="handleSubmit(createApplication)"
            >
              {{ $t("applications.create") }}
            </b-button>
          </div>
        </ValidationObserver>
        <div v-if="errorsMessages.length">
          <div v-for="msg in errorsMessages" :key="msg">
            <b-message
                :aria-close-label="$t('message.close')"
                :title="$t('message.app-config-error')"
                class="mt-4"
                has-icon
                type="is-danger"
            >
              <span
                  v-if="msg.mess"
                  class="columns"
                  style="margin: 10px; font-weight: bold"
                  v-html="msg.mess"
              />
              <span v-if="msg.param" class="columns" style="margin: 0"
              ><p style="width: 1650px">{{ msg.param }}</p></span
              >
              <span v-else v-html="msg"/>
            </b-message>
          </div>
        </div>
      </div>
    </PageView>
  </div>
</template>

<script>
import {Component, Vue} from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import {ValidationObserver, ValidationProvider} from "vee-validate";
import {ApplicationConfig} from "@/model/ApplicationConfig";
import {ApplicationService} from "@/services/rest/ApplicationService";
import {AlertService} from "@/services/AlertService";
import {ErrorsService} from "@/services/ErrorsService";
import {HttpStatusCodes} from "@/utils/HttpUtils";

@Component({
  components: {PageView, ValidationObserver, ValidationProvider},
})
export default class ApplicationCreationView extends Vue {
  applicationService = ApplicationService.INSTANCE;
  errorsService = ErrorsService.INSTANCE;
  alertService = AlertService.INSTANCE;

  applicationConfig = new ApplicationConfig();
  btnUpdateConfig = false;
  errorsMessages = [];
  error = [];
  comment = "";
  regExp = /^[a-zA-Z]+$/;

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

  validNameApplication(name) {
    return this.regExp.test(name);
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

  showHelp() {
    let routeData = this.$router.resolve("/help");
    window.open(routeData.href, "_blank");
  }

  async testApplication() {
    const loadingComponent = this.$buefy.loading.open();
    this.errorsMessages = [];
    try {
      let response = await this.applicationService.validateConfiguration(this.applicationConfig);
      if (!this.validNameApplication(response.result.application.name.toLowerCase())) {
        response.valid = false;
        response.validationCheckResults.push({
          "level": "ERROR",
          "message": "characterNotAcceptInName",
          "messageParams": {
            "name": response.result.application.name
          },
          "error": true,
          "success": false
        })
      }
      if (response.valid === true) {
        this.applicationConfig.name = response.result.application.name.toLowerCase();
        this.applicationConfig.version = response.result.application.version;
        if (response.result.application.version !== 1) {
          this.btnUpdateConfig = true;
        }
        this.alertService.toastSuccess(this.$t("alert.application-validate-success"));
      } else {
        for (let i = 0; i < response.validationCheckResults.length; i++) {
          if (
              this.errorsService.getErrorsMessages(response.validationCheckResults)[i] ===
              this.$t("errors.exception")
          ) {
            this.error[i] = {
              ...this.error[i],
              mess: this.errorsService.getErrorsMessages(response.validationCheckResults)[i],
              param: response.validationCheckResults[i].message.toString().split(),
            };
            this.errorsMessages.push(this.error[i]);
          } else {
            this.errorsMessages = this.errorsService.getErrorsMessages(
                response.validationCheckResults
            );
          }
        }
      }
    } catch (error) {
      this.checkMessageErrors(error);
    }
    loadingComponent.close();
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
}
</script>
