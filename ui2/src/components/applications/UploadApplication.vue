<template>
  <div>
    <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
      <ValidationProvider
        rules="required"
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
            v-model="login"
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
          :class="{ 'has-name': file && file.name }"
          :type="{
            'is-danger': errors && errors.length > 0,
            'is-success': valid,
          }"
        >
          <b-upload v-model="file" class="file-label" accept=".yaml">
            <span class="file-cta">
              <b-icon class="file-icon" icon="upload"></b-icon>
              <span class="file-label">{{
                $t("applications.chose-config")
              }}</span>
            </span>
            <span class="file-name" v-if="file">
              {{ file.name }}
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

@Component({
  components: { PageView, ValidationObserver, ValidationProvider },
})
export default class UploadApplication extends Vue {
  file = {};

  createApplication() {
    console.log("CREAE");
  }
}
</script>
