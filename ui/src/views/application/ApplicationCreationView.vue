<template>
  <div>
    <PageView>
      <h1 class="title main-title">{{ $t("titles.application-creation") }}</h1>
      <div>
        <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
          <div class="columns">
            <ValidationProvider
              class="column is-4"
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
                <b-upload v-model="applicationConfig.file" class="file-label" accept=".yaml, .zip">
                  <span class="file-cta">
                    <b-icon class="file-icon" icon="upload"></b-icon>
                    <span class="file-label">{{ $t("applications.chose-config") }}</span>
                  </span>
                  <span class="file-name" v-if="applicationConfig.file">
                    {{ applicationConfig.file.name }}
                  </span>
                </b-upload>
                <sup>
                  <b-tooltip :label="$t('applications.help_config')" position="is-right">
                    <a @click="showHelp" style="color: #006464ff; margin-left: 10px"
                      ><b-icon icon="question-circle"> </b-icon
                    ></a>
                  </b-tooltip>
                </sup>
              </b-field>
            </ValidationProvider>
            <div style="margin: 5px" class="column is-4">
              <b-button type="is-light" @click="handleSubmit(testApplication)" icon-left="vial">
                {{ $t("applications.test") }}
              </b-button>
            </div>
            <div class="column is-4">
              <b-tag v-if="btnUpdateConfig" type="is-warning" size="is-large" style="margin: 5px">
                {{ $t("applications.app_version") }}{{ applicationConfig.version }}
              </b-tag>
            </div>
          </div>
          <div class="columns">
            <ValidationProvider
              v-if="applicationConfig.name"
              class="column"
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
          </div>
          <div class="columns">
            <b-field class="column" :label="$t('dataTypesRepository.comment')" expanded>
              <b-input v-model="comment" maxlength="200" type="textarea"></b-input>
            </b-field>
          </div>
          <div class="buttons">
            <b-button
              v-if="btnUpdateConfig"
              type="is-warning"
              @click="handleSubmit(changeConfiguration)"
              icon-left="edit"
            >
              {{ $t("applications.change") }}
            </b-button>
            <b-button
              v-if="applicationConfig.name !== '' && !btnUpdateConfig"
              type="is-primary"
              @click="handleSubmit(createApplication)"
              icon-left="plus"
            >
              {{ $t("applications.create") }}
            </b-button>
          </div>
        </ValidationObserver>
        <div v-if="errorsMessages.length">
          <div v-for="msg in errorsMessages" :key="msg">
            <b-message
              :title="$t('message.app-config-error')"
              type="is-danger"
              has-icon
              :aria-close-label="$t('message.close')"
              class="mt-4"
            >
              <span
                v-if="msg.mess"
                v-html="msg.mess"
                class="columns"
                style="margin: 10px; font-weight: bold"
              />
              <span v-if="msg.param" class="columns" style="margin: 0"
                ><p style="width: 1650px">{{ msg.param }}</p></span
              >
              <span v-else v-html="msg" />
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

@Component({
  components: { PageView, ValidationObserver, ValidationProvider },
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
  showHelp() {
    let routeData = this.$router.resolve("/help");
    window.open(routeData.href, "_blank");
  }

  async testApplication() {
    this.errorsMessages = [];
    try {
      let response = await this.applicationService.validateConfiguration(this.applicationConfig);
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
