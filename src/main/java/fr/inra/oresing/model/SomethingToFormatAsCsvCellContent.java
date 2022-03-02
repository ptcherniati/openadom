package fr.inra.oresing.model;

/**
 * Représente une information qui a vocation à être formattée pour être écrite dans une cellule d'un fichier CSV
 */
public interface SomethingToFormatAsCsvCellContent {

    /**
     * La valeur après son formattage
     */
    String getAsContentForCsvCell();
}
