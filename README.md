#include <iostream>
#include <string>
#include <vector>
#include <list>
#include <stack>
#include <queue>
#include <unordered_map>
#include <map>

using namespace std;

// Structure for Scholarship
struct Scholarship {
    int id;
    string name;
    string description;
    double amount;
};

// Structure for Student Application
struct Application {
    int id;
    string studentName;
    int scholarshipId;
    string status; // Pending, Awarded, Rejected
};

// Global data structures
vector<Scholarship> scholarships;                  // Array for scholarships
list<Application> applications;                   // Linked list for applications
stack<vector<Scholarship>> undoStack, redoStack;  // Stacks for undo/redo
queue<Application*> reviewQueue;                  // Queue for review
unordered_map<int, Scholarship> scholarshipMap;   // Hash table for scholarships
map<int, Application> awardedApplications;        // Tree for awarded applications

// Function prototypes
void addScholarship();
void updateScholarship();
void deleteScholarship();
void displayScholarships();

void addApplication();
void updateApplication();
void deleteApplication();
void displayApplications();

void reviewApplications();
void awardScholarship();
void displayAwardedScholarships();

void generateReports();

// Main function
int main() {
    int choice;
    do {
        cout << "\nScholarship Application Tracker and Management System\n";
        cout << "1. Manage Scholarship Opportunities\n";
        cout << "2. Manage Student Applications\n";
        cout << "3. Review and Award Applications\n";
        cout << "4. Generate Reports and Analytics\n";
        cout << "5. Exit\n";
        cout << "Enter your choice: ";
        cin >> choice;

        switch (choice) {
        case 1:
            addScholarship();
            break;
        case 2:
            addApplication();
            break;
        case 3:
            reviewApplications();
            break;
        case 4:
            generateReports();
            break;
        case 5:
            cout << "Exiting system.\n";
            break;
        default:
            cout << "Invalid choice. Please try again.\n";
        }
    } while (choice != 5);

    return 0;
}

// Functions for Scholarship Management
void addScholarship() {
    Scholarship s;
    cout << "Enter Scholarship ID: ";
    cin >> s.id;
    cin.ignore();
    cout << "Enter Scholarship Name: ";
    getline(cin, s.name);
    cout << "Enter Description: ";
    getline(cin, s.description);
    cout << "Enter Amount: ";
    cin >> s.amount;

    scholarships.push_back(s);
    scholarshipMap[s.id] = s;
    undoStack.push(scholarships);

    cout << "Scholarship added successfully.\n";
}

void updateScholarship() {
    int id;
    cout << "Enter Scholarship ID to update: ";
    cin >> id;

    for (auto &s : scholarships) {
        if (s.id == id) {
            cout << "Enter new Scholarship Name: ";
            cin.ignore();
            getline(cin, s.name);
            cout << "Enter new Description: ";
            getline(cin, s.description);
            cout << "Enter new Amount: ";
            cin >> s.amount;

            scholarshipMap[s.id] = s;
            cout << "Scholarship updated successfully.\n";
            return;
        }
    }
    cout << "Scholarship ID not found.\n";
}

void deleteScholarship() {
    int id;
    cout << "Enter Scholarship ID to delete: ";
    cin >> id;

    scholarships.erase(
        remove_if(scholarships.begin(), scholarships.end(), [id](const Scholarship &s) {
            return s.id == id;
        }),
        scholarships.end());

    scholarshipMap.erase(id);
    cout << "Scholarship deleted successfully.\n";
}

void displayScholarships() {
    cout << "\nScholarship Opportunities:\n";
    for (const auto &s : scholarships) {
        cout << "ID: " << s.id << ", Name: " << s.name << ", Amount: $" << s.amount << endl;
    }
}

// Functions for Application Management
void addApplication() {
    Application a;
    cout << "Enter Application ID: ";
    cin >> a.id;
    cin.ignore();
    cout << "Enter Student Name: ";
    getline(cin, a.studentName);
    cout << "Enter Scholarship ID: ";
    cin >> a.scholarshipId;
    a.status = "Pending";

    applications.push_back(a);
    reviewQueue.push(&applications.back());

    cout << "Application added successfully.\n";
}

void displayApplications() {
    cout << "\nStudent Applications:\n";
    for (const auto &a : applications) {
        cout << "ID: " << a.id << ", Student: " << a.studentName << ", Status: " << a.status << endl;
    }
}

void reviewApplications() {
    while (!reviewQueue.empty()) {
        Application *app = reviewQueue.front();
        reviewQueue.pop();

        cout << "Reviewing Application ID: " << app->id << "\n";
        cout << "Student Name: " << app->studentName << "\n";
        cout << "Scholarship ID: " << app->scholarshipId << "\n";

        cout << "Enter status (Awarded/Rejected): ";
        cin >> app->status;

        if (app->status == "Awarded") {
            awardedApplications[app->id] = *app;
        }
    }
    cout << "All applications reviewed.\n";
}

void displayAwardedScholarships() {
    cout << "\nAwarded Scholarships:\n";
    for (const auto &entry : awardedApplications) {
        cout << "Application ID: " << entry.first << ", Student: " << entry.second.studentName << endl;
    }
}

void generateReports() {
    cout << "\nGenerating Reports...\n";
    displayScholarships();
    displayApplications();
    displayAwardedScholarships();
}
