<template>
  <ValidationObserver ref="observer" v-slot="{ handleSubmit }">
    <section>
      <InputWithValidation
        rules="required|email"
        type="email"
        :placeholder="$t('login.email-placeholder')"
        :label="$t('login.email')"
        v-model="email"
      />

      <InputWithValidation
        rules="required"
        type="password"
        :passwordReveal="true"
        :placeholder="$t('login.pwd-placeholder')"
        :label="$t('login.pwd')"
        vid="password"
        v-model="password"
      />
    </section>

    <div class="buttons">
      <b-button type="is-primary" @click="handleSubmit(submit)">
        {{ $t("login.signin") }}
      </b-button>
      <router-link :to="{ path: '/forgot-password/' + email }">
        {{ $t("login.pwd-forgotten") }}
      </router-link>
    </div>
  </ValidationObserver>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";
import { ValidationObserver } from "vee-validate";
import InputWithValidation from "../validation/InputWithValidation.vue";

@Component({
  components: { ValidationObserver, InputWithValidation },
})
export default class SignIn extends Vue {
  email = "";
  password = "";

  submit() {
    console.log(this.email, this.password);
  }
}
</script>
