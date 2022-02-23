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
              <b-upload v-model="applicationConfig.file" class="file-label" accept=".yaml, .zip">
                <span class="file-cta">
                  <b-icon class="file-icon" icon="upload"></b-icon>
                  <span class="file-label">{{ $t("applications.chose-config") }}</span>
                </span>
                <span class="file-name" v-if="applicationConfig.file">
                  {{ applicationConfig.file.name }}
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
}
</script>
