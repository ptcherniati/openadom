<template>
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
        name="uploadApplications"
        v-slot="{ errors, valid }"
        vid="uploadApplications"
      >
        <b-field
          class="file is-primary"
          :type="{
            'is-danger': errors && errors.length > 0,
            'is-success': valid,
          }"
        >
          <b-upload
            v-model="applicationConfig.file"
            class="file-label"
            accept=".yaml"
          >
            <span class="file-cta">
              <b-icon class="file-icon" icon="upload"></b-icon>
              <span class="file-label">{{
                $t("applications.chose-config")
              }}</span>
            </span>
            <span class="file-name" v-if="applicationConfig.file">
              {{ applicationConfig.file.name }}
            </span>
          </b-upload>
        </b-field>
      </ValidationProvider>
      <div class="buttons">
        <b-button
          type="is-primary"
          @click="handleSubmit(createApplication)"
          icon-right="plus"
        >
          {{ $t("applications.create") }}
        </b-button>
      </div>
    </ValidationObserver>
  </div>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { ValidationObserver, ValidationProvider } from "vee-validate";
import { ApplicationConfig } from "@/model/ApplicationConfig";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AlertService } from "@/services/AlertService";

@Component({
  components: { PageView, ValidationObserver, ValidationProvider },
})
export default class UploadApplication extends Vue {
  applicationService = ApplicationService.INSTANCE;
  alertService = AlertService.INSTANCE;

  applicationConfig = new ApplicationConfig();

  async createApplication() {
    try {
      await this.applicationService.createApplication(this.applicationConfig);
      this.alertService.toastSuccess(
        this.$t("alert.application-creation-success")
      );
    } catch (error) {
      this.alertService.toastError(this.$t("alert.server-error"), error);
    }
  }
}
</script>
