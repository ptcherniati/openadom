package fr.inra.oresing.persistence;

public enum OperationType {
    /*
        delegation of one's own rights
     */
    admin,

    /*
        can deposit a file without publishing it (in the case of a deposit on a repository)
        don't exist otherwise
     */
    depot,

    /*
        can delete a file that is not published (in the case of a deposit on a repository)
        don't exist otherwise
     */
    delete,

    /*
        can publish / unpublish a file already deposited (in the case of a deposit on a repository)
        otherwise can save or modify data by depositing a file
     */
    publication,

    /*
        can view the data or download it
     */
    extraction;
}