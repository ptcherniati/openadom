<template>
  <PageView>
    <h1 class="title main-title">{{ $t("titles.applications-page") }}</h1>
    <div class="buttons" v-if="canCreateApplication">
      <b-button type="is-primary" @click="createApplication" icon-right="plus">
        {{ $t("applications.create") }}
      </b-button>
    </div>

    <div class="container has-text-centered">
      <div class="columns is-mobile is-centered" style="flex-wrap: wrap; margin:0px;">
        <div v-for="application in applications" :key="application.name">
          <div class="column">
            <div class="card">
              <div class="card-header">
                <div class="title card-header-title" style="margin-top: 0; text-transform: uppercase; margin-bottom: 0px;">
                  <p field="name"> {{ application.name }}</p>
                </div>
                <section>
                  <b-button icon-left="external-link-square-alt"
                      type="is-primary"
                      size="is-medium"
                      @click="isCardModalActive = true" style="margin: 5px; opacity: 50%; color: #00a3a6; background-color: transparent;"/>
                  <b-modal v-model="isCardModalActive">
                    <div class="card">
                      <div class="card-header">
                        <div class="title card-header-title" style="margin-top: 0; text-transform: uppercase; margin-bottom: 0px;">
                          <p field="name"> {{ application.name }}</p>
                        </div>
                      </div>
                      <div class="card-content">
                        <div class="content">
                          <h4>Bienvenue sur le SI du Système d’Information sur les Produits Résiduaires Organiques (SI PRO) du SOERE PRO (Système d’observation et d’expérimentation pour la recherche en environnement).</h4>
                          <p>Le SOERE PRO est un observatoire de recherche en environnement composé de dispositifs expérimentaux au champ dédiés à l’étude des effets à long terme du recyclage agricole des PRO. Le réseau de sites est labellisé en tant que SOERE par ALLENVI depuis 2011, avec renouvellement de la labellisation en 2015. Il est aussi intégré à l’infrastructure ANAEE-France depuis 2013 ANNAEE France
                            Le Système d’information sur les Produits Résiduaires Organiques (SI PRO) est élaboré par l’INRA UMR ECOSYS et ECOINFORMATIQUE, en partenariat avec les équipes pilotes des sites du SOERE PRO (CIRAD, INRA) et des partenaires filières (Arvalis, LDAR, IFV, ITAB).Le SI archive les données acquises sur des dispositifs expérimentaux au champ du SOERE PRO et de partenaires filières, il archive également les métadonnées associées à ces jeux de données (ex. descriptif du dispositif, contexte agro-pédoclimatique). Il a aussi pour vocation d’archiver les caractéristiques analytiques de PRO pouvant être épandus en agriculture ainsi que leurs variables d’obtentions (ex. composition, descriptif du procédé de traitement, nomenclature…).</p>
                          <h4>Comment accéder aux données ?</h4>
                          <p>Voir quelles sont les données actuellement disponibles dans la base de données.
                            Le système d'information contient des données qui sont en libre accès et des données accessibles après validation d'une demande spécifique auprès des responsables scientifiques. Dans tous les cas, vous devez vous connecter avant de pouvoir interroger la base de données et extraire des données pour vos besoins.</p>
                        </div>
                      </div>
                      <div class="card-footer">
                        <div class="card-footer-item">
                          <a icon-left="drafting-compass"
                              @click="displayReferencesManagement(application)">
                            {{ $t("applications.references") }}</a>
                        </div>
                        <div class="card-footer-item">
                          <a icon-left="poll"
                              @click="displayDataSetManagement(application)">
                            {{ $t("applications.dataset") }}</a>
                        </div>
                      </div>
                    </div>
                  </b-modal>
                </section>
              </div>
              <div class="card-content">
                <div class="content">
                  <p field="creationDate">{{ new Date(application.creationDate) }}</p>
                </div>
              </div>
              <div class="card-footer">
                <div class="card-footer-item">
                  <a icon-left="drafting-compass"
                      @click="displayReferencesManagement(application)">
                    {{ $t("applications.references") }}</a>
                </div>
                <div class="card-footer-item">
                  <a icon-left="poll"
                      @click="displayDataSetManagement(application)">
                    {{ $t("applications.dataset") }}</a>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
<!--
    <b-table
      :data="applications"
      :striped="true"
      :isFocusable="true"
      :isHoverable="true"
      :sticky-header="true"
      :paginated="true"
      :per-page="15"
      height="100%"
    >
      <b-table-column field="name" :label="$t('applications.name')" sortable v-slot="props">
        {{ props.row.name }}
      </b-table-column>
      <b-table-column
        field="creationDate"
        :label="$t('applications.creation-date')"
        sortable
        v-slot="props"
      >
        {{ new Date(props.row.creationDate) }}
      </b-table-column>
      <b-table-column field="actions" :label="$t('applications.actions')" v-slot="props">
        <b-button icon-left="drafting-compass" @click="displayReferencesManagement(props.row)">{{
          $t("applications.references")
        }}</b-button>
        <b-button icon-left="poll" @click="displayDataSetManagement(props.row)">{{
          $t("applications.dataset")
        }}</b-button>
      </b-table-column>
    </b-table>
    --->
  </PageView>
</template>

<script>
import { ApplicationService } from "@/services/rest/ApplicationService";
import { Component, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { LoginService } from "@/services/rest/LoginService";

@Component({
  components: { PageView },
})
export default class ApplicationsView extends Vue {
  applicationService = ApplicationService.INSTANCE;

  applications = [];
  canCreateApplication = LoginService.INSTANCE.getAuthenticatedUser().authorizedForApplicationCreation;
  isCardModalActive=false;

  created() {
    this.init();
  }

  async init() {
    this.applications = await this.applicationService.getApplications();
  }

  createApplication() {
    this.$router.push("/applicationCreation");
  }

  displayReferencesManagement(application) {
    if (!application) {
      return;
    }
    this.$router.push("/applications/" + application.name + "/references");
  }

  displayDataSetManagement(application) {
    if (!application) {
      return;
    }
    this.$router.push("/applications/" + application.name + "/dataTypes");
  }
}
</script>
