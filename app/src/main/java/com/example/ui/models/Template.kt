package com.example.ui.models

data class Template(
    val id: String,
    val name: String,
    val difficulty: Int, // 1-3
    val category: String,
    val description: String,
    val code: String,
    val concepts: List<String>
)

object TemplateProvider {
    val templates = listOf(
        Template(
            id = "hello_world",
            name = "Hello World",
            difficulty = 1,
            category = "Basics",
            description = "The classic first program. Teaches you how to print text to the console.",
            code = """
                #include <stdio.h>

                int main() {
                    printf("Hello, World!\n");
                    return 0;
                }
            """.trimIndent(),
            concepts = listOf("Main function", "Standard I/O", "Printing to console")
        ),
        Template(
            id = "calculator",
            name = "Calculator",
            difficulty = 1,
            category = "Math",
            description = "A simple calculator program that adds two numbers together.",
            code = """
                #include <stdio.h>

                int main() {
                    int num1 = 10;
                    int num2 = 20;
                    int sum = num1 + num2;
                    
                    printf("The sum of %d and %d is %d\n", num1, num2, sum);
                    
                    return 0;
                }
            """.trimIndent(),
            concepts = listOf("Variables", "Arithmetic operators", "Formatted printing")
        ),
        Template(
            id = "array_sorting",
            name = "Array Sorting",
            difficulty = 2,
            category = "Algorithms",
            description = "Implements the Bubble Sort algorithm to sort an array of integers.",
            code = """
                #include <stdio.h>

                void bubbleSort(int arr[], int n) {
                    for (int i = 0; i < n - 1; i++) {
                        for (int j = 0; j < n - i - 1; j++) {
                            if (arr[j] > arr[j + 1]) {
                                int temp = arr[j];
                                arr[j] = arr[j + 1];
                                arr[j + 1] = temp;
                            }
                        }
                    }
                }

                int main() {
                    int arr[] = {64, 34, 25, 12, 22, 11, 90};
                    int n = sizeof(arr) / sizeof(arr[0]);
                    
                    bubbleSort(arr, n);
                    
                    printf("Sorted array: \n");
                    for (int i = 0; i < n; i++) {
                        printf("%d ", arr[i]);
                    }
                    printf("\n");
                    
                    return 0;
                }
            """.trimIndent(),
            concepts = listOf("Arrays", "Loops", "Sorting algorithms", "Functions")
        ),
        Template(
            id = "linked_list",
            name = "Linked List",
            difficulty = 3,
            category = "Data Structures",
            description = "A basic implementation of a singly linked list with node insertion.",
            code = """
                #include <stdio.h>
                #include <stdlib.h>

                struct Node {
                    int data;
                    struct Node* next;
                };

                void insertAtBeginning(struct Node** head_ref, int new_data) {
                    struct Node* new_node = (struct Node*)malloc(sizeof(struct Node));
                    new_node->data = new_data;
                    new_node->next = (*head_ref);
                    (*head_ref) = new_node;
                }

                void printList(struct Node* node) {
                    while (node != NULL) {
                        printf(" %d ", node->data);
                        node = node->next;
                    }
                }

                int main() {
                    struct Node* head = NULL;
                    
                    insertAtBeginning(&head, 3);
                    insertAtBeginning(&head, 2);
                    insertAtBeginning(&head, 1);
                    
                    printf("Created Linked list is: ");
                    printList(head);
                    printf("\n");
                    
                    return 0;
                }
            """.trimIndent(),
            concepts = listOf("Structs", "Pointers", "Dynamic memory allocation", "Linked Lists")
        ),
        Template(
            id = "file_io",
            name = "File I/O",
            difficulty = 2,
            category = "I/O",
            description = "Demonstrates how to write text to a file and read it back.",
            code = """
                #include <stdio.h>
                #include <stdlib.h>

                int main() {
                    FILE *fptr;
                    char filename[] = "test.txt";
                    
                    // Writing to file
                    fptr = fopen(filename, "w");
                    if (fptr == NULL) {
                        printf("Error opening file!\n");
                        exit(1);
                    }
                    fprintf(fptr, "This is testing file I/O.\n");
                    fclose(fptr);
                    
                    // Reading from file
                    char buffer[100];
                    fptr = fopen(filename, "r");
                    if (fptr == NULL) {
                        printf("Error opening file!\n");
                        exit(1);
                    }
                    
                    printf("File contents:\n");
                    while(fgets(buffer, 100, fptr)) {
                        printf("%s", buffer);
                    }
                    fclose(fptr);
                    
                    return 0;
                }
            """.trimIndent(),
            concepts = listOf("File pointers", "File operations (open/read/write/close)", "Error handling")
        )
    )
}
